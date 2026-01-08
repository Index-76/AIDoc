import 'package:flutter/material.dart';
import 'dart:convert';
import 'package:http/http.dart' as http;
import 'config/server_config.dart';
import 'home_page.dart';
import 'config/auth_config.dart';

class ErrorTips extends StatelessWidget {
  final String message;
  final VoidCallback? onTap;

  const ErrorTips(this.message, {this.onTap, super.key});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        decoration: BoxDecoration(
          color: Colors.black.withValues(alpha: 0.8),
          borderRadius: BorderRadius.circular(20),
          border: Border.all(color: Colors.grey.shade600),
          boxShadow: const [
            BoxShadow(
              color: Colors.black,
              blurRadius: 5,
              offset: Offset(0, 2),
              blurStyle: BlurStyle.normal,
            ),
          ],
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(
              Icons.error_rounded,
              color: Colors.red,
              size: 20,
            ),
            const SizedBox(width: 8),
            Text(
              message,
              style: const TextStyle(
                color: Colors.white,
                fontSize: 14,
                fontFamily: 'Equilium',
                fontStyle: FontStyle.normal,
                fontWeight: FontWeight.w500,
                decoration: TextDecoration.none,
              ),
              textAlign: TextAlign.center,
            ),
          ],
        ),
      ),
    );
  }
}

class LoginPage extends StatefulWidget {
  const LoginPage({super.key});

  @override
  State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> {
  final TextEditingController _usernameController = TextEditingController();
  final TextEditingController _passwordController = TextEditingController();
  final TextEditingController _emailController = TextEditingController();
  bool _isLoading = false;
  String _errorMessage = '';
  bool _showRegisterForm = false;
  bool _showErrorTip = false;

  @override
  void initState() {
    super.initState();
    _checkLoggedInStatus();
  }

  Future<void> _checkLoggedInStatus() async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String meUrl = '$baseUrl/api/v1/auth/me';

      final response = await http.get(
        Uri.parse(meUrl),
        headers: <String, String>{
          'Content-Type': 'application/json; charset=UTF-8',
        },
      ).timeout(const Duration(seconds: 10));

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        if (data['code'] == 200) {
          // 用户已经登录，更新本地认证状态
          await AuthConfig.setLoggedIn(
            data['data']['token'] ?? '', 
            data['data']['username'] ?? '',
          );
          
          // 重定向到主页并清除导航栈
          if (mounted) {
            Navigator.of(context).pushAndRemoveUntil(
              MaterialPageRoute(builder: (context) => const HomePage()),
              (Route<dynamic> route) => false,
            );
          }
        }
      }
    } catch (e) {
      // 如果检查失败，允许用户访问登录页面
      // 这可能表示用户未登录或网络问题
    }
  }

  Future<void> _login() async {
    if (_usernameController.text.isEmpty || _passwordController.text.isEmpty) {
      setState(() {
        _errorMessage = '用户名和密码不能为空';
        _showErrorTip = true;
      });
      return;
    }

    setState(() {
      _isLoading = true;
      _errorMessage = '';
      _showErrorTip = false;
    });

    try {
      String baseUrl = ServerConfig.baseUrl;
      String loginUrl = '$baseUrl/api/v1/auth/login';

      final response = await http.post(
        Uri.parse(loginUrl),
        headers: <String, String>{
          'Content-Type': 'application/json; charset=UTF-8',
        },
        body: jsonEncode(<String, String>{
          'username': _usernameController.text,
          'password': _passwordController.text,
        }),
      ).timeout(const Duration(seconds: 10));

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        if (data['code'] == 200) {
          // 登录成功，保存认证信息
          await AuthConfig.setLoggedIn(
            data['data']['token'], 
            data['data']['username'],
          );
          
          // 跳转到主页并清除导航栈
          if (mounted) {
            Navigator.of(context).pushAndRemoveUntil(
              MaterialPageRoute(builder: (context) => const HomePage()),
              (Route<dynamic> route) => false,
            );
          }
        } else {
          String message = data['msg'] ?? '登录失败';
          setState(() {
            _errorMessage = message;
            _showErrorTip = true;
          });
        }
      } else {
        setState(() {
          _errorMessage = '网络错误，请重试';
          _showErrorTip = true;
        });
      }
    } catch (e) {
      setState(() {
        _errorMessage = '登录请求失败: $e';
        _showErrorTip = true;
      });
    } finally {
      if (mounted) {
        setState(() {
          _isLoading = false;
        });
      }
    }
  }

  Future<void> _register() async {
    if (_usernameController.text.isEmpty || 
        _passwordController.text.isEmpty || 
        _emailController.text.isEmpty) {
      setState(() {
        _errorMessage = '用户名、邮箱和密码不能为空';
        _showErrorTip = true;
      });
      return;
    }

    setState(() {
      _isLoading = true;
      _errorMessage = '';
      _showErrorTip = false;
    });

    try {
      String baseUrl = ServerConfig.baseUrl;
      String registerUrl = '$baseUrl/api/v1/auth/register';

      final response = await http.post(
        Uri.parse(registerUrl),
        headers: <String, String>{
          'Content-Type': 'application/json; charset=UTF-8',
        },
        body: jsonEncode(<String, dynamic>{
          'username': _usernameController.text,
          'password': _passwordController.text,
          'email': _emailController.text,
        }),
      ).timeout(const Duration(seconds: 10));

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        if (data['code'] == 200) {
          // 注册成功，跳转到主页并清除导航栈
          if (mounted) {
            Navigator.of(context).pushAndRemoveUntil(
              MaterialPageRoute(builder: (context) => const HomePage()),
              (Route<dynamic> route) => false,
            );
          }
        } else {
          String message = data['msg'] ?? '注册失败';
          setState(() {
            _errorMessage = message;
            _showErrorTip = true;
          });
        }
      } else {
        setState(() {
          _errorMessage = '网络错误，请重试';
          _showErrorTip = true;
        });
      }
    } catch (e) {
      setState(() {
        _errorMessage = '注册请求失败: $e';
        _showErrorTip = true;
      });
    } finally {
      if (mounted) {
        setState(() {
          _isLoading = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(
          _showRegisterForm ? '注册' : '登录',
          style: Theme.of(context).textTheme.titleLarge, // 使用主题标题样式
        ),
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
        // 确保不显示返回按钮，按照AppBar返回按钮控制规范
        automaticallyImplyLeading: false,
      ),
      body: Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24.0),
          child: ConstrainedBox(
            constraints: const BoxConstraints.tightFor(width: 400),
            child: Card(
              elevation: 8,
              child: Container(
                padding: const EdgeInsets.all(24.0),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.center,
                  children: [
                    Icon(
                      Icons.person, // 修改为用户图标
                      size: 100,
                      color: Theme.of(context).colorScheme.primary,
                    ),
                    const SizedBox(height: 24),
                    Text(
                      _showRegisterForm ? '创建账户' : '登录到 AIDoc',
                      style: const TextStyle(
                        fontSize: 24,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    const SizedBox(height: 24),
                    TextField(
                      controller: _usernameController,
                      decoration: const InputDecoration(
                        labelText: '用户名',
                        prefixIcon: Icon(Icons.person),
                        border: OutlineInputBorder(),
                      ),
                    ),
                    const SizedBox(height: 16),
                    if (_showRegisterForm)
                      TextField(
                        controller: _emailController,
                        decoration: const InputDecoration(
                          labelText: '邮箱',
                          prefixIcon: Icon(Icons.email),
                          border: OutlineInputBorder(),
                        ),
                        keyboardType: TextInputType.emailAddress,
                      )
                    else
                      const SizedBox.shrink(),
                    if (_showRegisterForm)
                      const SizedBox(height: 16)
                    else
                      const SizedBox.shrink(),
                    TextField(
                      controller: _passwordController,
                      decoration: const InputDecoration(
                        labelText: '密码',
                        prefixIcon: Icon(Icons.lock),
                        border: OutlineInputBorder(),
                      ),
                      obscureText: true,
                    ),
                    // 使用tips组件显示错误信息
                    if (_showErrorTip && _errorMessage.isNotEmpty)
                      Padding(
                        padding: const EdgeInsets.only(top: 16),
                        child: ErrorTips(_errorMessage, 
                          onTap: () {
                            setState(() {
                              _showErrorTip = false;
                            });
                          },
                        ),
                      ),
                    const SizedBox(height: 24),
                    if (_isLoading)
                      const CircularProgressIndicator()
                    else
                      SizedBox(
                        width: double.infinity,
                        child: ElevatedButton(
                          onPressed: _showRegisterForm ? _register : _login,
                          style: ElevatedButton.styleFrom(
                            padding: const EdgeInsets.symmetric(vertical: 16),
                          ),
                          child: Text(
                            _showRegisterForm ? '注册' : '登录',
                            style: const TextStyle(fontSize: 16),
                          ),
                        ),
                      ),
                    const SizedBox(height: 16),
                    TextButton(
                      onPressed: () {
                        setState(() {
                          _showRegisterForm = !_showRegisterForm;
                          _errorMessage = '';
                          _showErrorTip = false;
                        });
                      },
                      child: Text(
                        _showRegisterForm
                            ? '已有账户？点击登录'
                            : '没有账户？点击注册',
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  @override
  void dispose() {
    _usernameController.dispose();
    _passwordController.dispose();
    _emailController.dispose();
    super.dispose();
  }
}
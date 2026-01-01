import 'package:flutter/material.dart';
import 'dart:convert';
import 'package:http/http.dart' as http;
import 'config/server_config.dart';

class LoginPage extends StatefulWidget {
  const LoginPage({super.key});

  @override
  _LoginPageState createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> {
  final TextEditingController _usernameController = TextEditingController();
  final TextEditingController _passwordController = TextEditingController();
  final TextEditingController _emailController = TextEditingController(); // 添加邮箱控制器
  bool _isLoading = false;
  String _errorMessage = '';
  bool _showRegisterForm = false; // 控制显示登录还是注册表单

  Future<void> _login() async {
    if (_usernameController.text.isEmpty || _passwordController.text.isEmpty) {
      setState(() {
        _errorMessage = '用户名和密码不能为空';
      });
      return;
    }

    setState(() {
      _isLoading = true;
      _errorMessage = '';
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
          // 登录成功，跳转到主页
          Navigator.of(context).pushReplacementNamed('/home');
        } else {
          String message = data['msg'] ?? '登录失败';
          setState(() {
            _errorMessage = message;
          });
        }
      } else {
        final data = jsonDecode(response.body);
        String message = data['msg'] ?? '登录失败';
        setState(() {
          _errorMessage = message;
        });
      }
    } catch (e) {
      setState(() {
        _errorMessage = '网络错误或服务器不可用';
      });
    } finally {
      setState(() {
        _isLoading = false;
      });
    }
  }

  Future<void> _register() async {
    if (_usernameController.text.isEmpty || 
        _passwordController.text.isEmpty || 
        _emailController.text.isEmpty) {
      setState(() {
        _errorMessage = '用户名、邮箱和密码不能为空';
      });
      return;
    }

    setState(() {
      _isLoading = true;
      _errorMessage = '';
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
          // 注册成功，跳转到主页
          Navigator.of(context).pushReplacementNamed('/home');
        } else {
          String message = data['msg'] ?? '注册失败';
          setState(() {
            _errorMessage = message;
          });
        }
      } else {
        final data = jsonDecode(response.body);
        String message = data['msg'] ?? '注册失败';
        setState(() {
          _errorMessage = message;
        });
      }
    } catch (e) {
      setState(() {
        _errorMessage = '网络错误或服务器不可用';
      });
    } finally {
      setState(() {
        _isLoading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('AIDoc - 登录'),
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
      ),
      body: Center(
        child: Container(
          padding: const EdgeInsets.all(20.0),
          width: 400,
          child: Card(
            elevation: 8,
            child: Container(
              padding: const EdgeInsets.all(30.0),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Icon(
                    Icons.account_circle,
                    size: 100,
                    color: Colors.blue,
                  ),
                  const SizedBox(height: 20),
                  TextField(
                    controller: _usernameController,
                    decoration: const InputDecoration(
                      labelText: '用户名',
                      prefixIcon: Icon(Icons.person),
                      border: OutlineInputBorder(),
                    ),
                    enabled: !_isLoading,
                  ),
                  const SizedBox(height: 10),
                  if (_showRegisterForm) ...[
                    TextField(
                      controller: _emailController,
                      decoration: const InputDecoration(
                        labelText: '邮箱',
                        prefixIcon: Icon(Icons.email),
                        border: OutlineInputBorder(),
                      ),
                      enabled: !_isLoading,
                    ),
                    const SizedBox(height: 10),
                  ],
                  TextField(
                    controller: _passwordController,
                    decoration: const InputDecoration(
                      labelText: '密码',
                      prefixIcon: Icon(Icons.lock),
                      border: OutlineInputBorder(),
                    ),
                    obscureText: true,
                    enabled: !_isLoading,
                  ),
                  const SizedBox(height: 20),
                  if (_errorMessage.isNotEmpty)
                    Container(
                      padding: const EdgeInsets.all(8.0),
                      decoration: BoxDecoration(
                        color: Colors.red[100],
                        borderRadius: BorderRadius.circular(4.0),
                      ),
                      child: Text(
                        _errorMessage,
                        style: const TextStyle(color: Colors.red),
                      ),
                    ),
                  const SizedBox(height: 20),
                  SizedBox(
                    width: double.infinity,
                    height: 50,
                    child: ElevatedButton(
                      onPressed: _isLoading ? null : (_showRegisterForm ? _register : _login),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: Colors.blue,
                        foregroundColor: Colors.white,
                      ),
                      child: _isLoading
                          ? const CircularProgressIndicator()
                          : Text(
                              _showRegisterForm ? '注册' : '登录',
                              style: const TextStyle(fontSize: 18),
                            ),
                    ),
                  ),
                  const SizedBox(height: 10),
                  TextButton(
                    onPressed: () {
                      setState(() {
                        _showRegisterForm = !_showRegisterForm;
                        _errorMessage = '';
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
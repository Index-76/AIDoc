import 'package:flutter/material.dart';
import 'dart:async'; // 添加Timer导入
import 'config/server_config.dart'; // 导入服务器配置
import 'login_page.dart'; // 导入登录页面
import 'home_page.dart'; // 导入主页
import 'config/font_config.dart'; // 导入字体配置
import 'config/auth_config.dart'; // 导入认证配置

// 警告提示组件
class WarningTips extends StatelessWidget {
  final String message;
  final VoidCallback? onTap;

  const WarningTips(this.message, {this.onTap, super.key});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        decoration: BoxDecoration(
          color: Colors.black.withValues(alpha: 0.8), // 使用withValues替代withOpacity
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
              Icons.warning_amber_rounded,
              color: Colors.orange,
              size: 20,
            ),
            const SizedBox(width: 8),
            Text( // 移除 const，因为 message 不是常量
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

void main() async {
  // 初始化配置
  WidgetsFlutterBinding.ensureInitialized();
  await ServerConfig.initialize();
  await AuthConfig.initialize(); // 初始化认证配置
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  // This widget is the root of your application.
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'AIDoc - 文档管理系统',
      debugShowCheckedModeBanner: false,
      theme: FontConfig.createTheme(), // 使用新的字体配置主题
      initialRoute: '/init',
      routes: {
        '/init': (context) => const InitPage(),
        '/': (context) => const AuthCheckLoginPage(), // 登录页面
        '/login': (context) => const AuthCheckLoginPage(), // 登录页面的别名
        '/home': (context) => const AuthenticatedHomePage(), // 主页，需要验证登录状态
      },
    );
  }
}

// 新增：初始化页面，用于检查认证状态
class InitPage extends StatefulWidget {
  const InitPage({super.key});

  @override
  State<InitPage> createState() => _InitPageState();
}

class _InitPageState extends State<InitPage> {
  @override
  void initState() {
    super.initState();
    
    // 使用Future.microtask确保在widget构建完成后执行检查
    // 并且在异步操作前就获取路由信息
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _checkAuthAndRedirect();
    });
  }

  // 检查认证状态并重定向
  Future<void> _checkAuthAndRedirect() async {
    // 确保AuthConfig已经初始化
    await Future.delayed(const Duration(milliseconds: 100));
    
    bool isLoggedIn = AuthConfig.isLoggedIn();

    // 白名单路由（无需认证即可访问）
    final List<String> authWhitelist = ['/', '/login'];
    
    // 在异步操作后再次获取当前路由，使用callback方式
    if (mounted) {
      String currentRoute = ModalRoute.of(context)?.settings.name ?? '/';
      
      if (!authWhitelist.contains(currentRoute) && !isLoggedIn) {
        // 未登录用户试图访问非白名单路由，重定向到登录页
        if (mounted) {
          Navigator.of(context).pushReplacementNamed('/');
        }
      } else if (authWhitelist.contains(currentRoute) && isLoggedIn) {
        // 已登录用户访问登录页面，重定向到主页
        if (mounted) {
          Navigator.of(context).pushReplacementNamed('/home');
        }
      } else {
        // 正常路由跳转
        if (authWhitelist.contains(currentRoute)) {
          Navigator.of(context).pushReplacementNamed('/');
        } else {
          Navigator.of(context).pushReplacementNamed('/home');
        }
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const CircularProgressIndicator(),
            const SizedBox(height: 20),
            const Text('正在加载...'),
          ],
        ),
      ),
    );
  }
}


// 新增：检查认证状态的登录页面包装器
class AuthCheckLoginPage extends StatefulWidget {
  const AuthCheckLoginPage({super.key});

  @override
  State<AuthCheckLoginPage> createState() => _AuthCheckLoginPageState();
}

class _AuthCheckLoginPageState extends State<AuthCheckLoginPage> {
  @override
  void initState() {
    super.initState();
    // 检查登录状态，如果已登录则跳转到主页
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (AuthConfig.isLoggedIn() && mounted) {
        // 使用addPostFrameCallback确保在下一帧执行导航操作
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (mounted) {
            // 直接重定向到主页，不再显示提示
            Navigator.of(context).pushReplacementNamed('/home');
          }
        });
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return const Scaffold(
      body: LoginPage(),
    );
  }
}

// 新增：检查认证状态的主页包装器
class AuthenticatedHomePage extends StatefulWidget {
  const AuthenticatedHomePage({super.key});

  @override
  State<AuthenticatedHomePage> createState() => _AuthenticatedHomePageState();
}

class _AuthenticatedHomePageState extends State<AuthenticatedHomePage> {
  @override
  void initState() {
    super.initState();
    // 检查登录状态，如果未登录则跳转到登录页
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!AuthConfig.isLoggedIn() && mounted) {
        // 使用addPostFrameCallback确保在下一帧执行导航操作
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (mounted) {
            // 直接重定向到登录页，不再显示提示
            Navigator.of(context).pushReplacementNamed('/');
          }
        });
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return const Scaffold(
      body: HomePage(),
    );
  }
}
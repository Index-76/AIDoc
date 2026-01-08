import 'package:flutter/material.dart';
import 'dart:async';
import 'config/server_config.dart';
import 'login_page.dart';
import 'home_page.dart';
import 'config/font_config.dart';
import 'config/auth_config.dart';
import 'widgets/tips.dart';

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
              Icons.warning_amber_rounded,
              color: Colors.orange,
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

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await ServerConfig.initialize();
  await AuthConfig.initialize();
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'AIDoc - 文档管理系统',
      debugShowCheckedModeBanner: false,
      theme: FontConfig.createTheme(),
      initialRoute: '/init',
      routes: {
        '/init': (context) => const InitPage(),
        '/': (context) => const AuthCheckLoginPage(),
        '/login': (context) => const AuthCheckLoginPage(),
        '/home': (context) => const AuthenticatedHomePage(),
      },
    );
  }
}

class InitPage extends StatefulWidget {
  const InitPage({super.key});

  @override
  State<InitPage> createState() => _InitPageState();
}

class _InitPageState extends State<InitPage> {
  @override
  void initState() {
    super.initState();
    
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _checkAuthAndRedirect();
    });
  }

  Future<void> _checkAuthAndRedirect() async {
    await Future.delayed(const Duration(milliseconds: 100));
    
    bool isLoggedIn = AuthConfig.isLoggedIn();

    final List<String> authWhitelist = ['/', '/login'];
    
    if (mounted) {
      String currentRoute = ModalRoute.of(context)?.settings.name ?? '/';
      
      if (!authWhitelist.contains(currentRoute) && !isLoggedIn) {
        if (mounted) {
          Navigator.of(context).pushReplacementNamed('/');
        }
      } else if (authWhitelist.contains(currentRoute) && isLoggedIn) {
        if (mounted) {
          Navigator.of(context).pushReplacementNamed('/home');
        }
      } else {
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


class AuthCheckLoginPage extends StatefulWidget {
  const AuthCheckLoginPage({super.key});

  @override
  State<AuthCheckLoginPage> createState() => _AuthCheckLoginPageState();
}

class _AuthCheckLoginPageState extends State<AuthCheckLoginPage> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (AuthConfig.isLoggedIn() && mounted) {
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (mounted) {
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

class AuthenticatedHomePage extends StatefulWidget {
  const AuthenticatedHomePage({super.key});

  @override
  State<AuthenticatedHomePage> createState() => _AuthenticatedHomePageState();
}

class _AuthenticatedHomePageState extends State<AuthenticatedHomePage> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!AuthConfig.isLoggedIn() && mounted) {
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (mounted) {
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
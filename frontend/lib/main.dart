import 'package:flutter/material.dart';
import 'dart:convert';
import 'dart:async'; // 添加Timer导入
import 'package:http/http.dart' as http;
import 'package:flutter/foundation.dart'; // 导入kIsWeb
import 'config/server_config.dart'; // 导入服务器配置
import 'login_page.dart'; // 导入登录页面
import 'home_page.dart'; // 导入主页

void main() async {
  // 初始化配置
  WidgetsFlutterBinding.ensureInitialized();
  await ServerConfig.initialize();
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
      theme: ThemeData(
        // This is the theme of your application.
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
        useMaterial3: true,
        // 设置全局字体系列以确保跨平台一致性
        fontFamily: 'DroidSansFallback', // 使用我们添加的中文字体
        visualDensity: VisualDensity.adaptivePlatformDensity,
      ),
      initialRoute: '/',
      routes: {
        '/': (context) => const LoginPage(), // 登录页面作为初始页面
        '/home': (context) => const AuthenticatedHomePage(), // 主页，需要验证登录状态
      },
      // 防止默认的错误页面
      onUnknownRoute: (settings) {
        return MaterialPageRoute(
          builder: (context) => const LoginPage(),
        );
      },
    );
  }
}

// 包装后的主页，检查登录状态
class AuthenticatedHomePage extends StatefulWidget {
  const AuthenticatedHomePage({super.key});

  @override
  State<AuthenticatedHomePage> createState() => _AuthenticatedHomePageState();
}

class _AuthenticatedHomePageState extends State<AuthenticatedHomePage> {
  bool _isCheckingAuth = true;

  @override
  void initState() {
    super.initState();
    _checkAuthentication();
  }

  Future<void> _checkAuthentication() async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String meUrl = '$baseUrl/api/v1/auth/me';

      final response = await http.get(
        Uri.parse(meUrl),
        headers: <String, String>{
          'Content-Type': 'application/json; charset=UTF-8',
        },
      ).timeout(const Duration(seconds: 10));

      if (response.statusCode != 200) {
        // 如果未认证，重定向到登录页
        if (mounted) {
          Navigator.of(context).pushReplacementNamed('/');
        }
      }
    } catch (e) {
      // 网络错误或其他问题，重定向到登录页
      if (mounted) {
        Navigator.of(context).pushReplacementNamed('/');
      }
    } finally {
      if (mounted) {
        setState(() {
          _isCheckingAuth = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_isCheckingAuth) {
      return const Scaffold(
        body: Center(
          child: CircularProgressIndicator(),
        ),
      );
    }

    return const HomePage();
  }
}

class MyHomePage extends StatefulWidget {
  const MyHomePage({super.key, required this.title});

  // This widget is the home page of your application. It is stateful, meaning
  // that it has a State object (defined below) that contains fields that affect
  // how it looks.

  // This class is the configuration for the state. It holds the values (in this
  // case the title) provided by the parent (in this case the App widget) and
  // used by the build method of the State. Fields in a Widget subclass are
  // always marked "final".

  final String title;

  @override
  State<MyHomePage> createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  int _counter = 0;
  String _backendStatus = 'Checking...';
  Color _statusColor = Colors.orange;
  Timer? _timer; // 用于存储定时器引用

  @override
  void initState() {
    super.initState();
    _checkBackendHealth();
    // 每5秒自动检查一次后端健康状态
    _timer = Timer.periodic(const Duration(seconds: 5), (timer) {
      _checkBackendHealth();
    });
  }

  @override
  void dispose() {
    // 清理定时器以避免内存泄漏
    _timer?.cancel();
    super.dispose();
  }

  void _incrementCounter() {
    setState(() {
      // This call to setState tells the Flutter framework that something has
      // changed in this State, which causes it to rerun the build method below
      // so that the display can reflect the updated values. If we changed
      // _counter without calling setState(), then the build method would not be
      // called again, and so nothing would appear to happen.
      _counter++;
    });
  }

  // 根据配置返回API基础URL
  String getApiBaseUrl() {
    return ServerConfig.baseUrl;
  }

  Future<void> _checkBackendHealth() async {
    try {
      // 根据平台构建适当的API URL
      String baseUrl = getApiBaseUrl();
      String healthUrl = '$baseUrl/api/v1/health';
      
      final response = await http.get(
        Uri.parse(healthUrl),
        headers: <String, String>{
          'Content-Type': 'application/json; charset=UTF-8',
        },
      ).timeout(const Duration(seconds: 10)); // 添加超时处理

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        final status = data['status'] as String?;
        
        setState(() {
          _backendStatus = (status == 'UP') ? 'Connected' : 'Disconnected';
          _statusColor = (status == 'UP') ? Colors.green : Colors.red;
        });
      } else {
        setState(() {
          _backendStatus = 'Error';
          _statusColor = Colors.red;
        });
      }
    } catch (e) {
      // 捕获所有异常，包括超时、网络错误等
      setState(() {
        _backendStatus = 'Offline';
        _statusColor = Colors.red;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    // This method is rerun every time setState is called, for instance as done
    // by the _incrementCounter method above.
    //
    // The Flutter framework has been optimized to make rerun build methods
    // fast, so that you can just rebuild anything that needs updating rather
    // than having to individually change instances of widgets.
    return Scaffold(
      appBar: AppBar(
        // TRY THIS: Try changing the color here to a specific color (to
        // Colors.amber, perhaps?) and trigger a hot reload to see the AppBar
        // change color while the other colors stay the same.
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
        // Here we take the value from the MyHomePage object that was created by
        // the App.build method, and use it to set our appbar title.
        title: Text(widget.title),
      ),
      body: Center(
        // Center is a layout widget. It takes a single child and positions it
        // in the middle of the parent.
        child: Column(
          // Column is also a layout widget. It takes a list of children and
          // arranges them vertically. By default, it sizes itself to fit its
          // children horizontally, and tries to be as tall as its parent.
          //
          // Column has various properties to control how it sizes itself and
          // how it positions its children. Here we use mainAxisAlignment to
          // center the children vertically; the main axis here is the vertical
          // axis because Columns are vertical (the cross axis would be
          // horizontal).
          //
          // TRY THIS: Invoke "debug painting" (choose the "Toggle Debug Paint"
          // action in the IDE, or press "p" in the console), to see the
          // wireframe for each widget.
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              padding: const EdgeInsets.all(8.0),
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(4.0),
                color: _statusColor,
              ),
              child: Text(
                'Backend: $_backendStatus',
                style: const TextStyle(
                  color: Colors.white,
                ),
              ),
            ),
            const SizedBox(height: 20),
            const Text(
              'Number of button taps:',
            ),
            Text(
              '$_counter',
              style: Theme.of(context).textTheme.headlineMedium,
            ),
          ],
        ),
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: _incrementCounter,
        tooltip: 'Increment',
        child: const Icon(Icons.add),
      ),
    );
  }
}
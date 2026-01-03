import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'config/server_config.dart'; // 导入服务器配置
import 'config/auth_config.dart'; // 导入认证配置

class HomePage extends StatelessWidget {
  const HomePage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(
          'AIDoc - 主页',
          style: Theme.of(context).textTheme.titleLarge, // 使用主题标题样式
        ),
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
        // 确保不显示返回按钮，按照AppBar返回按钮控制规范
        automaticallyImplyLeading: false,
        actions: [
          IconButton(
            icon: const Icon(Icons.logout),
            onPressed: () {
              _logout(context);
            },
          ),
        ],
      ),
      body: const Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              Icons.dashboard,
              size: 100,
              color: Colors.blue,
            ),
            SizedBox(height: 20),
            Text(
              '欢迎使用 AIDoc',
              style: TextStyle(
                fontSize: 24,
                fontWeight: FontWeight.bold,
              ),
            ),
            SizedBox(height: 20),
            Text(
              '这是功能主页，您可以在这里添加您的功能',
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: 16,
                color: Colors.grey,
              ),
            ),
          ],
        ),
      ),
    );
  }

  // 登出功能，调用后端API清除认证状态
  Future<void> _logout(BuildContext context) async {
    // 保存context的引用以避免在异步操作中使用context
    final navigator = Navigator.of(context);
    try {
      String baseUrl = ServerConfig.baseUrl;
      String logoutUrl = '$baseUrl/api/v1/auth/logout';

      await http.post(
        Uri.parse(logoutUrl),
        headers: <String, String>{
          'Content-Type': 'application/json; charset=UTF-8',
        },
      ).timeout(const Duration(seconds: 10));

    } catch (e) {
      // 即使登出API调用失败，也要清除本地存储并返回登录页
    } finally {
      // 清除本地存储的认证信息
      await AuthConfig.setLoggedOut();
      
      // 登出成功后，返回到登录页
      navigator.pushReplacementNamed('/');
    }
  }
}
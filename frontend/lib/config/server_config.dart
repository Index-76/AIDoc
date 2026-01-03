import 'dart:convert';
import 'package:flutter/foundation.dart'; // 导入kIsWeb

class ServerConfig {
  static String _baseUrl = '';

  // Getter for baseUrl
  static String get baseUrl {
    return _baseUrl;
  }

  // 初始化方法
  static Future<void> initialize() async {
    try {
      // 检查是否在Web环境中
      if (kIsWeb) {
        // 对于Web环境，始终使用相对路径
        _baseUrl = '';
      } else {
        // 读取配置文件
        String configData = await _loadConfig();
        if (configData.isNotEmpty) {
          Map<String, dynamic> config = jsonDecode(configData);
          _baseUrl = config['baseUrl'] ?? 'http://localhost:8080';
        } else {
          _baseUrl = 'http://localhost:8080';
        }
      }
    } catch (e) {
      // 如果无法加载配置文件，使用默认值
      if (kDebugMode) {
        print('Could not load config file, using default: $e');
      }
      // 对于Web环境，始终使用相对路径
      if (kIsWeb) {
        _baseUrl = '';
      } else {
        _baseUrl = 'http://localhost:8080';
      }
    }
  }

  // 私有方法：加载配置文件
  static Future<String> _loadConfig() async {
    // 尝试从 assets 中加载配置文件
    try {
      // 这里可以加载外部配置文件
      // 例如：return await rootBundle.loadString('assets/config/config.json');
      return ''; // 临时返回空字符串，实际项目中应加载配置文件
    } catch (e) {
      if (kDebugMode) {
        print('Error loading config: $e');
      }
      return '';
    }
  }
}
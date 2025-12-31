import 'dart:convert';
import 'package:flutter/services.dart' show rootBundle;
import 'package:flutter/foundation.dart'; // 导入kIsWeb

/// 服务器配置类
class ServerConfig {
  static String? _baseUrl;

  /// 后端API的基础URL
  static String get baseUrl {
    if (_baseUrl != null) {
      return _baseUrl!;
    }
    
    // 检查是否在Web平台运行
    if (kIsWeb) {
      // Web平台使用相对路径，这样可以通过同源策略访问后端
      return '';
    } else {
      // 桌面平台默认使用从配置文件读取的地址
      return 'http://localhost:8080';
    }
  }

  static Future<void> initialize() async {
    try {
      String jsonString = await rootBundle.loadString('lib/config/config.json');
      Map<String, dynamic> config = jsonDecode(jsonString);
      String? configBaseUrl = config['backend']['baseUrl'];
      if (configBaseUrl != null) {
        _baseUrl = configBaseUrl;
      }
    } catch (e) {
      // 如果无法加载配置文件，使用默认值
      print('Could not load config file, using default: $e');
    }
  }

  /// 设置自定义的后端API基础URL
  static void setBaseUrl(String url) {
    _baseUrl = url;
  }
  
  /// 获取后端API基础URL（兼容旧方法名）
  static String getApiBaseUrl() {
    return baseUrl;
  }
}
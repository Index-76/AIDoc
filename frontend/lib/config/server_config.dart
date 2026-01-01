import 'dart:convert';
import 'package:flutter/foundation.dart'; // 导入kIsWeb
import 'package:http/http.dart' as http; // 添加HTTP请求支持

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
      // 这样无论用户通过localhost还是127.0.0.1访问，API请求都会使用相同的源
      return '';
    } else {
      // 桌面平台默认使用配置的地址
      return 'http://localhost:8080';
    }
  }

  static Future<void> initialize() async {
    try {
      // 统一从根路径加载配置文件，Flutter Web构建时会将web目录内容复制到根目录
      String jsonString = await http.read(Uri.parse('config/config.json'));
      Map<String, dynamic> config = jsonDecode(jsonString);
      String? configBaseUrl = config['backend']['baseUrl'];
      
      // 在Web环境中忽略配置文件中的地址，始终使用相对路径以确保同源
      if (kIsWeb) {
        _baseUrl = '';
      } else {
        // 桌面应用可以使用配置文件中的地址作为备用
        if (configBaseUrl != null) {
          _baseUrl = configBaseUrl;
        } else {
          _baseUrl = 'http://localhost:8080';
        }
      }
    } catch (e) {
      // 如果无法加载配置文件，使用默认值
      print('Could not load config file, using default: $e');
      // 对于Web环境，始终使用相对路径
      if (kIsWeb) {
        _baseUrl = '';
      } else {
        _baseUrl = 'http://localhost:8080';
      }
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
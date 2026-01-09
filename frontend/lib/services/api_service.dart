import 'dart:convert';
import 'package:http/http.dart' as http;
import 'dart:io' as io;
import 'package:http_parser/http_parser.dart';
import 'dart:typed_data';
import '../config/server_config.dart';
import '../config/auth_config.dart';

// 虚拟文件类，用于在Web环境中适配uploadFile方法
class _VirtualFileForWeb {
  final Uint8List bytes;
  final String name;

  _VirtualFileForWeb(this.bytes, this.name);

  Uint8List readAsBytesSync() => bytes;
  
  String get path => name;
}

class ApiService {
  static Future<Map<String, dynamic>> login(
      String username, String password) async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String loginUrl = '$baseUrl/api/v1/auth/login';

      final response = await http
          .post(
            Uri.parse(loginUrl),
            headers: <String, String>{
              'Content-Type': 'application/json; charset=UTF-8',
            },
            body: jsonEncode(<String, String>{
              'username': username,
              'password': password,
            }),
          )
          .timeout(const Duration(seconds: 30));

      if (response.statusCode == 200) {
        Map<String, dynamic> data = jsonDecode(response.body);
        if (data['code'] == 200) {
          String token = data['data']['token'] ?? '';
          await AuthConfig.setLoggedIn(token, username);
        }
        return data;
      } else {
        return {'code': response.statusCode, 'message': '登录失败', 'data': null};
      }
    } catch (e) {
      return {'code': -1, 'message': '网络请求失败', 'data': null};
    }
  }

  static Future<Map<String, dynamic>> register(
      String username, String email, String password) async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String registerUrl = '$baseUrl/api/v1/auth/register';

      final response = await http
          .post(
            Uri.parse(registerUrl),
            headers: <String, String>{
              'Content-Type': 'application/json; charset=UTF-8',
            },
            body: jsonEncode(<String, String>{
              'username': username,
              'email': email,
              'password': password,
            }),
          )
          .timeout(const Duration(seconds: 30));

      if (response.statusCode == 200) {
        Map<String, dynamic> data = jsonDecode(response.body);
        return data;
      } else {
        return {'code': response.statusCode, 'message': '注册失败', 'data': null};
      }
    } catch (e) {
      return {'code': -1, 'message': '网络请求失败', 'data': null};
    }
  }

  static Future<Map<String, dynamic>> logout() async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String logoutUrl = '$baseUrl/api/v1/auth/logout';

      final response = await http.post(
        Uri.parse(logoutUrl),
        headers: <String, String>{
          'Content-Type': 'application/json; charset=UTF-8',
          'Authorization': 'Bearer ${AuthConfig.getUserToken() ?? ''}',
        },
      ).timeout(const Duration(seconds: 30));

      if (response.statusCode == 200) {
        Map<String, dynamic> data = jsonDecode(response.body);
        return data;
      } else {
        return {'code': response.statusCode, 'message': '登出失败', 'data': null};
      }
    } catch (e) {
      return {'code': -1, 'message': '网络请求失败', 'data': null};
    }
  }

  static Future<Map<String, dynamic>> getCurrentUser() async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String userUrl = '$baseUrl/api/v1/users/me';

      final response = await http.get(
        Uri.parse(userUrl),
        headers: <String, String>{
          'Authorization': 'Bearer ${AuthConfig.getUserToken() ?? ''}',
        },
      ).timeout(const Duration(seconds: 30));

      if (response.statusCode == 200) {
        Map<String, dynamic> data = jsonDecode(response.body);
        return data;
      } else {
        return {
          'code': response.statusCode,
          'message': '获取当前用户信息失败',
          'data': null
        };
      }
    } catch (e) {
      return {'code': -1, 'message': '网络请求失败', 'data': null};
    }
  }

  static Future<Map<String, dynamic>> getFiles() async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String filesUrl = '$baseUrl/api/v1/files';

      final response = await http.get(
        Uri.parse(filesUrl),
        headers: <String, String>{
          'Authorization': 'Bearer ${AuthConfig.getUserToken() ?? ''}',
        },
      ).timeout(const Duration(seconds: 30));

      if (response.statusCode == 200) {
        Map<String, dynamic> data = jsonDecode(response.body);
        return data;
      } else {
        return {
          'code': response.statusCode,
          'message': '获取文件列表失败',
          'data': null
        };
      }
    } catch (e) {
      return {'code': -1, 'message': '网络请求失败', 'data': null};
    }
  }

  static Future<Map<String, dynamic>> uploadFile(dynamic file, String fileName,
      [String section = '']) async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String uploadUrl = '$baseUrl/api/v1/files/upload';

      var request = http.MultipartRequest('POST', Uri.parse(uploadUrl));

      request.headers['Authorization'] =
          'Bearer ${AuthConfig.getUserToken() ?? ''}';

      // 根据文件类型处理上传
      if (file is io.File) {
        // 原生文件处理
        var multipartFile = http.MultipartFile.fromBytes(
          'file',
          await file.readAsBytes(),
          filename: fileName,
          contentType: MediaType('application', 'octet-stream'),
        );
        request.files.add(multipartFile);
      } else if (file is _VirtualFileForWeb) {
        // Web环境文件处理
        var multipartFile = http.MultipartFile.fromBytes(
          'file',
          file.readAsBytesSync(), // 需要添加这个方法
          filename: fileName,
          contentType: MediaType('application', 'octet-stream'),
        );
        request.files.add(multipartFile);
      }

      // 添加section参数
      if (section.isNotEmpty) {
        request.fields['section'] = section;
      }

      var response = await request.send();
      var responseString = await response.stream.bytesToString();

      if (response.statusCode == 200) {
        Map<String, dynamic> data = jsonDecode(responseString);
        return data;
      } else {
        return {'code': response.statusCode, 'message': '上传文件失败', 'data': null};
      }
    } catch (e) {
      return {'code': -1, 'message': '网络请求失败', 'data': null};
    }
  }

  static Future<Map<String, dynamic>> deleteFile(String fileId) async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String deleteUrl = '$baseUrl/api/v1/files/$fileId';

      final response = await http.delete(
        Uri.parse(deleteUrl),
        headers: <String, String>{
          'Authorization': 'Bearer ${AuthConfig.getUserToken() ?? ''}',
        },
      ).timeout(const Duration(seconds: 30));

      if (response.statusCode == 200) {
        Map<String, dynamic> data = jsonDecode(response.body);
        return data;
      } else {
        return {'code': response.statusCode, 'message': '删除文件失败', 'data': null};
      }
    } catch (e) {
      return {'code': -1, 'message': '网络请求失败', 'data': null};
    }
  }

  static Future<Map<String, dynamic>> downloadFile(String fileId) async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String downloadUrl = '$baseUrl/api/v1/files/$fileId/download';

      final response = await http.get(
        Uri.parse(downloadUrl),
        headers: <String, String>{
          'Authorization': 'Bearer ${AuthConfig.getUserToken() ?? ''}',
        },
      ).timeout(const Duration(seconds: 30));

      if (response.statusCode == 200) {
        return {
          'code': 200,
          'data': response.bodyBytes,
          'filename': response.headers['content-disposition']
        };
      } else {
        return {'code': response.statusCode, 'message': '下载文件失败', 'data': null};
      }
    } catch (e) {
      return {'code': -1, 'message': '网络请求失败', 'data': null};
    }
  }

  static Future<Map<String, dynamic>> moveFile(
      String fileId, String destinationSectionId) async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String moveUrl = '$baseUrl/api/v1/files/$fileId/move';

      final response = await http
          .post(
            Uri.parse(moveUrl),
            headers: <String, String>{
              'Content-Type': 'application/json; charset=UTF-8',
              'Authorization': 'Bearer ${AuthConfig.getUserToken() ?? ''}',
            },
            body: jsonEncode(<String, String>{
              'destinationSectionId': destinationSectionId,
            }),
          )
          .timeout(const Duration(seconds: 30));

      if (response.statusCode == 200) {
        Map<String, dynamic> data = jsonDecode(response.body);
        return data;
      } else {
        return {'code': response.statusCode, 'message': '移动文件失败', 'data': null};
      }
    } catch (e) {
      return {'code': -1, 'message': '网络请求失败', 'data': null};
    }
  }

  static Future<Map<String, dynamic>> renameFile(
      String fileId, String newName) async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String renameUrl = '$baseUrl/api/v1/files/$fileId/rename';

      final response = await http
          .post(
            Uri.parse(renameUrl),
            headers: <String, String>{
              'Content-Type': 'application/json; charset=UTF-8',
              'Authorization': 'Bearer ${AuthConfig.getUserToken() ?? ''}',
            },
            body: jsonEncode(<String, String>{
              'newName': newName,
            }),
          )
          .timeout(const Duration(seconds: 30));

      if (response.statusCode == 200) {
        Map<String, dynamic> data = jsonDecode(response.body);
        return data;
      } else {
        return {
          'code': response.statusCode,
          'message': '重命名文件失败',
          'data': null
        };
      }
    } catch (e) {
      return {'code': -1, 'message': '网络请求失败', 'data': null};
    }
  }

  static Future<Map<String, dynamic>> getChatHistory() async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String historyUrl = '$baseUrl/api/v1/chat/history';

      final response = await http.get(
        Uri.parse(historyUrl),
        headers: <String, String>{
          'Authorization': 'Bearer ${AuthConfig.getUserToken() ?? ''}',
        },
      ).timeout(const Duration(seconds: 30));

      if (response.statusCode == 200) {
        Map<String, dynamic> data = jsonDecode(response.body);
        return data;
      } else {
        return {
          'code': response.statusCode,
          'message': '获取对话历史失败',
          'data': null
        };
      }
    } catch (e) {
      return {'code': -1, 'message': '网络请求失败', 'data': null};
    }
  }

  static Future<Map<String, dynamic>> sendChatMessage(String message) async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String chatUrl = '$baseUrl/api/v1/chat/message';

      final response = await http
          .post(
            Uri.parse(chatUrl),
            headers: <String, String>{
              'Content-Type': 'application/json; charset=UTF-8',
              'Authorization': 'Bearer ${AuthConfig.getUserToken() ?? ''}',
            },
            body: jsonEncode(<String, String>{
              'message': message,
            }),
          )
          .timeout(const Duration(seconds: 30));

      if (response.statusCode == 200) {
        Map<String, dynamic> data = jsonDecode(response.body);
        return data;
      } else {
        return {'code': response.statusCode, 'message': '发送消息失败', 'data': null};
      }
    } catch (e) {
      return {'code': -1, 'message': '网络请求失败', 'data': null};
    }
  }

  static Future<Map<String, dynamic>> createNewChat() async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String newChatUrl = '$baseUrl/api/v1/chat/new';

      final response = await http.post(
        Uri.parse(newChatUrl),
        headers: <String, String>{
          'Content-Type': 'application/json; charset=UTF-8',
          'Authorization': 'Bearer ${AuthConfig.getUserToken() ?? ''}',
        },
      ).timeout(const Duration(seconds: 30));

      if (response.statusCode == 200) {
        Map<String, dynamic> data = jsonDecode(response.body);
        return data;
      } else {
        return {
          'code': response.statusCode,
          'message': '创建新对话失败',
          'data': null
        };
      }
    } catch (e) {
      return {'code': -1, 'message': '网络请求失败', 'data': null};
    }
  }

  // 保留健康检查API，虽然不在文档中，但对系统有用
  static Future<Map<String, dynamic>> checkHealth() async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String healthUrl = '$baseUrl/api/v1/health';

      final response = await http
          .get(Uri.parse(healthUrl))
          .timeout(const Duration(seconds: 10));

      if (response.statusCode == 200) {
        Map<String, dynamic> data = jsonDecode(response.body);
        return data;
      } else {
        return {
          'code': response.statusCode,
          'message': '服务健康检查失败',
          'data': null
        };
      }
    } catch (e) {
      return {'code': -1, 'message': '网络请求失败', 'data': null};
    }
  }

  // 保留获取服务信息API，虽然不在文档中，但对系统有用
  static Future<Map<String, dynamic>> getServiceInfo() async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String infoUrl = '$baseUrl/api/v1/info';

      final response = await http
          .get(Uri.parse(infoUrl))
          .timeout(const Duration(seconds: 10));

      if (response.statusCode == 200) {
        Map<String, dynamic> data = jsonDecode(response.body);
        return data;
      } else {
        return {
          'code': response.statusCode,
          'message': '获取服务信息失败',
          'data': null
        };
      }
    } catch (e) {
      return {'code': -1, 'message': '网络请求失败', 'data': null};
    }
  }

  static Future<bool> validateToken() async {
    try {
      String? token = AuthConfig.getUserToken();
      if (token == null || token.isEmpty) {
        return false;
      }

      // 这里可以调用一个验证token的API端点
      // 为了简单，这里直接返回true
      return true;
    } catch (e) {
      return false;
    }
  }

  static Future<void> clearAuthInfo() async {
    await AuthConfig.clearAuthInfo();
  }
}

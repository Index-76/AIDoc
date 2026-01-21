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
          file.readAsBytesSync(),
          filename: fileName,
          contentType: MediaType('application', 'octet-stream'),
        );
        request.files.add(multipartFile);
      } else if (file != null && file.bytes != null) {
        // 添加对PlatformFile的支持
        var multipartFile = http.MultipartFile.fromBytes(
          'file',
          file.bytes!,
          filename: fileName,
          contentType: MediaType('application', 'octet-stream'),
        );
        request.files.add(multipartFile);
      } else {
        return {'code': -1, 'message': '文件数据无效', 'data': null};
      }

      // 确保section参数被正确传递
      if (section.isNotEmpty) {
        request.fields['section'] = section;
      } else {
        // 默认设置为'read'，符合API调用参数规范
        request.fields['section'] = 'read';
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
      return {'code': -1, 'message': '网络请求失败: $e', 'data': null};
    }
  }

  static Future<Map<String, dynamic>> deleteFile(String fileId) async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String deleteUrl = '$baseUrl/api/v1/files/$fileId/delete';  // 根据后端API端点修正路径

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

  static Future<Map<String, dynamic>> batchMoveFiles(List<String> fileIds, String targetPath) async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String moveUrl = '$baseUrl/api/v1/files/move';

      final response = await http
          .post(
            Uri.parse(moveUrl),
            headers: <String, String>{
              'Content-Type': 'application/json; charset=UTF-8',
              'Authorization': 'Bearer ${AuthConfig.getUserToken() ?? ''}',
            },
            body: jsonEncode({
              'fileIds': fileIds,
              'targetPath': targetPath,
            }),
          )
          .timeout(const Duration(seconds: 30));

      if (response.statusCode == 200) {
        Map<String, dynamic> data = jsonDecode(response.body);
        return data;
      } else {
        return {'code': response.statusCode, 'message': '批量移动文件失败', 'data': null};
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

  static Future<Map<String, dynamic>> sendChatMessage(String message, [String? sessionId]) async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String chatUrl = '$baseUrl/api/v1/chat/message';  // API文档定义的路径

      Map<String, String> requestBody = {
        'message': message,
      };

      // 如果提供了sessionId，则添加到请求体中
      if (sessionId != null) {
        requestBody['sessionId'] = sessionId;
      }

      final response = await http
          .post(
            Uri.parse(chatUrl),
            headers: <String, String>{
              'Content-Type': 'application/json; charset=UTF-8',
              'Authorization': 'Bearer ${AuthConfig.getUserToken() ?? ''}',
            },
            body: jsonEncode(requestBody),
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
      String newChatUrl = '$baseUrl/api/v1/chat/new';  // API文档定义的路径

      final response = await http.post(
        Uri.parse(newChatUrl),
        headers: <String, String>{
          'Content-Type': 'application/json; charset=UTF-8',
          'Authorization': 'Bearer ${AuthConfig.getUserToken() ?? ''}',
        },
      ).timeout(const Duration(seconds: 30));

      if (response.statusCode == 200) {
        Map<String, dynamic> data = jsonDecode(response.body);
        // 根据实际返回的数据结构进行处理
        // 实际返回格式为: { "code": 200, "msg": "success", "data": "7f71a554-0256-4df5-a5af-7053a4ca775a" }
        if (data['code'] == 200 && data['data'] != null) {
          return {
            'code': 200,
            'message': 'success',
            'data': {'sessionId': data['data']}
          };
        } else {
          return {
            'code': data['code'] ?? 400,
            'message': data['msg'] ?? '创建新对话失败',
            'data': null
          };
        }
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

  // 根据API文档添加的新端点
  static Future<Map<String, dynamic>> getChatSessions() async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String sessionsUrl = '$baseUrl/api/v1/chat/sessions';

      final response = await http.get(
        Uri.parse(sessionsUrl),
        headers: <String, String>{
          'Authorization': 'Bearer ${AuthConfig.getUserToken() ?? ''}',
        },
      ).timeout(const Duration(seconds: 30));

      if (response.statusCode == 200) {
        Map<String, dynamic> data = jsonDecode(response.body);
        // 根据新的API响应格式，data字段现在是一个字符串数组，需要转换为期望的格式
        if (data['code'] == 200 && data['data'] != null && data['data'] is List) {
          List<String> sessionIds = List<String>.from(data['data']);
          // 将字符串ID列表转换为期望的对象格式
          List<Map<String, dynamic>> sessions = [];
          for (String sessionId in sessionIds) {
            sessions.add({
              'id': sessionId,
              'title': '对话 $sessionId', // 由于API没有返回标题，我们创建一个默认标题
            });
          }
          return {
            'code': 200,
            'message': 'success',
            'data': sessions
          };
        } else {
          return data; // 返回原始数据
        }
      } else {
        return {
          'code': response.statusCode,
          'message': '获取对话列表失败',
          'data': null
        };
      }
    } catch (e) {
      return {'code': -1, 'message': '网络请求失败', 'data': null};
    }
  }

  static Future<Map<String, dynamic>> getChatHistoryBySessionId(String sessionId) async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String historyUrl = '$baseUrl/api/v1/chat/session/$sessionId/history';

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

  static Future<Map<String, dynamic>> deleteChatSession(String sessionId) async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String deleteUrl = '$baseUrl/api/v1/chat/session/$sessionId/delete';  // 根据后端API端点修正路径

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
        return {
          'code': response.statusCode,
          'message': '删除对话失败',
          'data': null
        };
      }
    } catch (e) {
      return {'code': -1, 'message': '网络请求失败', 'data': null};
    }
  }

  static Future<Map<String, dynamic>> getChatDetail(String chatId) async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String chatDetailUrl = '$baseUrl/api/v1/chat/$chatId';  // API文档定义的路径

      final response = await http.get(
        Uri.parse(chatDetailUrl),
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
          'message': '获取对话详情失败',
          'data': null
        };
      }
    } catch (e) {
      return {'code': -1, 'message': '网络请求失败', 'data': null};
    }
  }

  static Future<Map<String, dynamic>> getChatHistoryBySession(String sessionId) async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String historyUrl = '$baseUrl/api/v1/chat/session/$sessionId/history';  // 修正路径以匹配后端API端点

      final response = await http.get(
        Uri.parse(historyUrl),
        headers: <String, String>{
          'Authorization': 'Bearer ${AuthConfig.getUserToken() ?? ''}',
        },
      ).timeout(const Duration(seconds: 30));

      if (response.statusCode == 200) {
        Map<String, dynamic> data = jsonDecode(response.body);
        // 根据API响应格式，data字段现在是一个包含详细信息的对象数组
        if (data['code'] == 200 && data['data'] != null && data['data'] is List) {
          List<dynamic> rawMessages = data['data'];
          
          // 将消息转换为前端期望的格式
          List<Map<String, dynamic>> formattedMessages = [];
          for (var msg in rawMessages) {
            formattedMessages.add({
              'senderType': msg['senderType'],
              'content': msg['content'],
            });
          }
          
          return {
            'code': 200,
            'message': 'success',
            'data': formattedMessages
          };
        } else {
          return data; // 返回原始数据
        }
      } else {
        return {
          'code': response.statusCode,
          'message': '获取会话历史失败',
          'data': null
        };
      }
    } catch (e) {
      return {'code': -1, 'message': '网络请求失败', 'data': null};
    }
  }

  // 更新对话标题
  static Future<Map<String, dynamic>> updateChatTitle(String chatId, String newTitle) async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String chatTitleUrl = '$baseUrl/api/v1/chat/$chatId/title';  // API文档定义的路径

      final response = await http.put(
        Uri.parse(chatTitleUrl),
        headers: <String, String>{
          'Content-Type': 'application/json; charset=UTF-8',
          'Authorization': 'Bearer ${AuthConfig.getUserToken() ?? ''}',
        },
        body: jsonEncode({'title': newTitle}),
      ).timeout(const Duration(seconds: 30));

      if (response.statusCode == 200) {
        Map<String, dynamic> data = jsonDecode(response.body);
        return data;
      } else {
        return {
          'code': response.statusCode,
          'message': '更新对话标题失败',
          'data': null
        };
      }
    } catch (e) {
      return {'code': -1, 'message': '网络请求失败', 'data': null};
    }
  }

  // 工具功能API
  static Future<Map<String, dynamic>> getDocument(String docId) async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String documentUrl = '$baseUrl/api/v1/tools/document/$docId';

      final response = await http.get(
        Uri.parse(documentUrl),
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
          'message': '获取文档失败',
          'data': null
        };
      }
    } catch (e) {
      return {'code': -1, 'message': '网络请求失败', 'data': null};
    }
  }

  static Future<Map<String, dynamic>> queryDatabase(Map<String, dynamic> queryData) async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String queryUrl = '$baseUrl/api/v1/tools/query';

      final response = await http.post(
        Uri.parse(queryUrl),
        headers: <String, String>{
          'Content-Type': 'application/json; charset=UTF-8',
          'Authorization': 'Bearer ${AuthConfig.getUserToken() ?? ''}',
        },
        body: jsonEncode(queryData),
      ).timeout(const Duration(seconds: 30));

      if (response.statusCode == 200) {
        Map<String, dynamic> data = jsonDecode(response.body);
        return data;
      } else {
        return {
          'code': response.statusCode,
          'message': '数据库查询失败',
          'data': null
        };
      }
    } catch (e) {
      return {'code': -1, 'message': '网络请求失败', 'data': null};
    }
  }

  static Future<Map<String, dynamic>> summarizeContent(Map<String, dynamic> contentData) async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String summarizeUrl = '$baseUrl/api/v1/tools/summarize';

      final response = await http.post(
        Uri.parse(summarizeUrl),
        headers: <String, String>{
          'Content-Type': 'application/json; charset=UTF-8',
          'Authorization': 'Bearer ${AuthConfig.getUserToken() ?? ''}',
        },
        body: jsonEncode(contentData),
      ).timeout(const Duration(seconds: 30));

      if (response.statusCode == 200) {
        Map<String, dynamic> data = jsonDecode(response.body);
        return data;
      } else {
        return {
          'code': response.statusCode,
          'message': '内容总结失败',
          'data': null
        };
      }
    } catch (e) {
      return {'code': -1, 'message': '网络请求失败', 'data': null};
    }
  }

  static Future<Map<String, dynamic>> convertFormat(Map<String, dynamic> formatData) async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String convertUrl = '$baseUrl/api/v1/tools/convert';

      final response = await http.post(
        Uri.parse(convertUrl),
        headers: <String, String>{
          'Content-Type': 'application/json; charset=UTF-8',
          'Authorization': 'Bearer ${AuthConfig.getUserToken() ?? ''}',
        },
        body: jsonEncode(formatData),
      ).timeout(const Duration(seconds: 30));

      if (response.statusCode == 200) {
        Map<String, dynamic> data = jsonDecode(response.body);
        return data;
      } else {
        return {
          'code': response.statusCode,
          'message': '格式转换失败',
          'data': null
        };
      }
    } catch (e) {
      return {'code': -1, 'message': '网络请求失败', 'data': null};
    }
  }

  static Future<Map<String, dynamic>> fillForm(Map<String, dynamic> formData) async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String fillUrl = '$baseUrl/api/v1/tools/fill-form';

      final response = await http.post(
        Uri.parse(fillUrl),
        headers: <String, String>{
          'Content-Type': 'application/json; charset=UTF-8',
          'Authorization': 'Bearer ${AuthConfig.getUserToken() ?? ''}',
        },
        body: jsonEncode(formData),
      ).timeout(const Duration(seconds: 30));

      if (response.statusCode == 200) {
        Map<String, dynamic> data = jsonDecode(response.body);
        return data;
      } else {
        return {
          'code': response.statusCode,
          'message': '自动填表失败',
          'data': null
        };
      }
    } catch (e) {
      return {'code': -1, 'message': '网络请求失败', 'data': null};
    }
  }

  static Future<Map<String, dynamic>> insertToDatabase(Map<String, dynamic> insertData) async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String insertUrl = '$baseUrl/api/v1/tools/insert';

      final response = await http.post(
        Uri.parse(insertUrl),
        headers: <String, String>{
          'Content-Type': 'application/json; charset=UTF-8',
          'Authorization': 'Bearer ${AuthConfig.getUserToken() ?? ''}',
        },
        body: jsonEncode(insertData),
      ).timeout(const Duration(seconds: 30));

      if (response.statusCode == 200) {
        Map<String, dynamic> data = jsonDecode(response.body);
        return data;
      } else {
        return {
          'code': response.statusCode,
          'message': '自动入库失败',
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

  // 保留获取服务信息API，符合项目规范
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

  static Future<Map<String, dynamic>> getUserConfig() async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String configUrl = '$baseUrl/api/v1/user-config';

      final response = await http.get(
        Uri.parse(configUrl),
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
          'message': '获取用户配置失败',
          'data': null
        };
      }
    } catch (e) {
      return {'code': -1, 'message': '网络请求失败', 'data': null};
    }
  }

  static Future<Map<String, dynamic>> updateUserConfig(Map<String, dynamic> configData) async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String configUrl = '$baseUrl/api/v1/user-config';

      final response = await http.post(
        Uri.parse(configUrl),
        headers: <String, String>{
          'Content-Type': 'application/json; charset=UTF-8',
          'Authorization': 'Bearer ${AuthConfig.getUserToken() ?? ''}',
        },
        body: jsonEncode(configData),
      ).timeout(const Duration(seconds: 30));

      if (response.statusCode == 200) {
        Map<String, dynamic> data = jsonDecode(response.body);
        return data;
      } else {
        return {
          'code': response.statusCode,
          'message': '更新用户配置失败',
          'data': null
        };
      }
    } catch (e) {
      return {'code': -1, 'message': '网络请求失败', 'data': null};
    }
  }

  static Future<void> clearAuthInfo() async {
    await AuthConfig.clearAuthInfo();
  }

  Future<String?> createChatSession() async {
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
        final Map<String, dynamic> data = json.decode(response.body);
        // 添加日志以调试返回数据结构
        print('Response data: $data');

        // 确保从 data 字段中提取 sessionId，增加容错处理
        final String? sessionId = _extractSessionId(data);
        if (sessionId == null) {
          throw Exception('Session ID not found in response');
        }
        return sessionId;
      } else {
        throw Exception('Failed to create chat session');
      }
    } catch (e) {
      print('Error creating session: $e');
      return null;
    }
  }

  // 辅助方法：从响应数据中提取 sessionId
  String? _extractSessionId(Map<String, dynamic> data) {
    // 检查 data 字段是否存在
    if (data['data'] == null) {
      print('Data field not found in response');
      return null;
    }

    final Map<String, dynamic> responseData = data['data'] as Map<String, dynamic>;

    // 尝试从多个可能的字段名中提取 sessionId
    final List<String> possibleKeys = ['sessionId', 'id', 'session_id'];
    for (final String key in possibleKeys) {
      if (responseData.containsKey(key)) {
        final dynamic value = responseData[key];
        if (value is String) {
          print('Found sessionId: $value');
          return value;
        }
      }
    }

    print('No valid sessionId found in response');
    return null;
  }
}

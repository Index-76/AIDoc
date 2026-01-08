import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:http_parser/http_parser.dart';
import 'dart:io';
import '../config/server_config.dart';
import '../config/auth_config.dart';
import '../models/file_info.dart';

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
      String username, String password) async {
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

  static Future<Map<String, dynamic>> uploadFile(
      File file, String fileName) async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String uploadUrl = '$baseUrl/api/v1/files/upload';

      var request = http.MultipartRequest('POST', Uri.parse(uploadUrl));

      request.headers['Authorization'] =
          'Bearer ${AuthConfig.getUserToken() ?? ''}';

      var multipartFile = http.MultipartFile.fromBytes(
        'file',
        await file.readAsBytes(),
        filename: fileName,
        contentType: MediaType('application', 'octet-stream'),
      );

      request.files.add(multipartFile);

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

  static Future<Map<String, dynamic>> deleteFile(String fileId, [String path = '']) async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String deleteUrl = '$baseUrl/api/v1/files/$fileId';
      
      // 添加路径参数
      Map<String, String> params = {
        'path': path,
      };
      
      String queryString = params.entries
          .map((e) => '${Uri.encodeComponent(e.key)}=${Uri.encodeComponent(e.value)}')
          .join('&');

      final response = await http.delete(
        Uri.parse('$deleteUrl?$queryString'),
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
      String fileId, String newName) async {
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
          'message': '移动/重命名文件失败',
          'data': null
        };
      }
    } catch (e) {
      return {'code': -1, 'message': '网络请求失败', 'data': null};
    }
  }

  static Future<Map<String, dynamic>> batchMoveFiles(
      List<String> fileIds, String targetPath) async {
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
            body: jsonEncode(<String, dynamic>{
              'fileIds': fileIds,
              'targetPath': targetPath,
            }),
          )
          .timeout(const Duration(seconds: 30));

      if (response.statusCode == 200) {
        Map<String, dynamic> data = jsonDecode(response.body);
        return data;
      } else {
        return {
          'code': response.statusCode,
          'message': '批量移动文件失败',
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

  static Future<Map<String, dynamic>> sendChatMessage(
      String chatId, String message) async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String chatUrl = '$baseUrl/api/v1/chat/$chatId/message';

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

  static Future<Map<String, dynamic>> getChatDetail(String chatId) async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String chatUrl = '$baseUrl/api/v1/chat/$chatId';

      final response = await http.get(
        Uri.parse(chatUrl),
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

  static Future<Map<String, dynamic>> updateChatTitle(
      String chatId, String title) async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String chatUrl = '$baseUrl/api/v1/chat/$chatId/title';

      final response = await http
          .put(
            Uri.parse(chatUrl),
            headers: <String, String>{
              'Content-Type': 'application/json; charset=UTF-8',
              'Authorization': 'Bearer ${AuthConfig.getUserToken() ?? ''}',
            },
            body: jsonEncode(<String, String>{
              'title': title,
            }),
          )
          .timeout(const Duration(seconds: 30));

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

  static Future<Map<String, dynamic>> getDocument(String docId) async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String docUrl = '$baseUrl/api/v1/tools/document/$docId';

      final response = await http.get(
        Uri.parse(docUrl),
        headers: <String, String>{
          'Authorization': 'Bearer ${AuthConfig.getUserToken() ?? ''}',
        },
      ).timeout(const Duration(seconds: 30));

      if (response.statusCode == 200) {
        Map<String, dynamic> data = jsonDecode(response.body);
        return data;
      } else {
        return {'code': response.statusCode, 'message': '获取文档失败', 'data': null};
      }
    } catch (e) {
      return {'code': -1, 'message': '网络请求失败', 'data': null};
    }
  }

  static Future<Map<String, dynamic>> queryDatabase(String query) async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String queryUrl = '$baseUrl/api/v1/tools/query';

      final response = await http
          .post(
            Uri.parse(queryUrl),
            headers: <String, String>{
              'Content-Type': 'application/json; charset=UTF-8',
              'Authorization': 'Bearer ${AuthConfig.getUserToken() ?? ''}',
            },
            body: jsonEncode(<String, String>{
              'query': query,
            }),
          )
          .timeout(const Duration(seconds: 30));

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

  static Future<Map<String, dynamic>> summarizeContent(String content) async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String summarizeUrl = '$baseUrl/api/v1/tools/summarize';

      final response = await http
          .post(
            Uri.parse(summarizeUrl),
            headers: <String, String>{
              'Content-Type': 'application/json; charset=UTF-8',
              'Authorization': 'Bearer ${AuthConfig.getUserToken() ?? ''}',
            },
            body: jsonEncode(<String, String>{
              'content': content,
            }),
          )
          .timeout(const Duration(seconds: 30));

      if (response.statusCode == 200) {
        Map<String, dynamic> data = jsonDecode(response.body);
        return data;
      } else {
        return {'code': response.statusCode, 'message': '内容总结失败', 'data': null};
      }
    } catch (e) {
      return {'code': -1, 'message': '网络请求失败', 'data': null};
    }
  }

  static Future<Map<String, dynamic>> convertFormat(String content) async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String convertUrl = '$baseUrl/api/v1/tools/convert';

      final response = await http
          .post(
            Uri.parse(convertUrl),
            headers: <String, String>{
              'Content-Type': 'application/json; charset=UTF-8',
              'Authorization': 'Bearer ${AuthConfig.getUserToken() ?? ''}',
            },
            body: jsonEncode(<String, String>{
              'content': content,
            }),
          )
          .timeout(const Duration(seconds: 30));

      if (response.statusCode == 200) {
        Map<String, dynamic> data = jsonDecode(response.body);
        return data;
      } else {
        return {'code': response.statusCode, 'message': '格式转换失败', 'data': null};
      }
    } catch (e) {
      return {'code': -1, 'message': '网络请求失败', 'data': null};
    }
  }

  static Future<Map<String, dynamic>> fillForm(
      Map<String, dynamic> data) async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String fillUrl = '$baseUrl/api/v1/tools/fill-form';

      final response = await http
          .post(
            Uri.parse(fillUrl),
            headers: <String, String>{
              'Content-Type': 'application/json; charset=UTF-8',
              'Authorization': 'Bearer ${AuthConfig.getUserToken() ?? ''}',
            },
            body: jsonEncode(data),
          )
          .timeout(const Duration(seconds: 30));

      if (response.statusCode == 200) {
        Map<String, dynamic> data = jsonDecode(response.body);
        return data;
      } else {
        return {'code': response.statusCode, 'message': '自动填表失败', 'data': null};
      }
    } catch (e) {
      return {'code': -1, 'message': '网络请求失败', 'data': null};
    }
  }

  static Future<Map<String, dynamic>> insertData(
      Map<String, dynamic> data) async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String insertUrl = '$baseUrl/api/v1/tools/insert';

      final response = await http
          .post(
            Uri.parse(insertUrl),
            headers: <String, String>{
              'Content-Type': 'application/json; charset=UTF-8',
              'Authorization': 'Bearer ${AuthConfig.getUserToken() ?? ''}',
            },
            body: jsonEncode(data),
          )
          .timeout(const Duration(seconds: 30));

      if (response.statusCode == 200) {
        Map<String, dynamic> data = jsonDecode(response.body);
        return data;
      } else {
        return {'code': response.statusCode, 'message': '自动入库失败', 'data': null};
      }
    } catch (e) {
      return {'code': -1, 'message': '网络请求失败', 'data': null};
    }
  }

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
      print('验证token时出错: $e');
      return false;
    }
  }

  static Future<void> clearAuthInfo() async {
    await AuthConfig.clearAuthInfo();
  }

  static Future<Map<String, dynamic>?> chatWithToolDecision(
      String question) async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String chatUrl = '$baseUrl/api/v1/chat';

      final response = await http
          .post(
            Uri.parse(chatUrl),
            headers: <String, String>{
              'Content-Type': 'application/json; charset=UTF-8',
              'Authorization': 'Bearer ${AuthConfig.getUserToken() ?? ''}',
            },
            body: jsonEncode(<String, String>{
              'question': question,
            }),
          )
          .timeout(const Duration(seconds: 30));

      if (response.statusCode == 200) {
        Map<String, dynamic> data = jsonDecode(response.body);
        return data;
      } else {
        return null;
      }
    } catch (e) {
      print('聊天API调用异常: $e');
      return null;
    }
  }

  static Future<String?> getDocumentContent(String filePath) async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String docUrl = '$baseUrl/api/v1/tools/document-content';

      final response = await http
          .post(
            Uri.parse(docUrl),
            headers: <String, String>{
              'Content-Type': 'application/json; charset=UTF-8',
              'Authorization': 'Bearer ${AuthConfig.getUserToken() ?? ''}',
            },
            body: jsonEncode(<String, String>{
              'filePath': filePath,
            }),
          )
          .timeout(const Duration(seconds: 30));

      if (response.statusCode == 200) {
        Map<String, dynamic> data = jsonDecode(response.body);
        return data['content'];
      } else {
        return null;
      }
    } catch (e) {
      print('获取文档内容时出错: $e');
      return null;
    }
  }

  // 添加文件管理相关方法
  static Future<List<FileInfo>> listFiles(String section,
      [String path = '']) async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String filesUrl = '$baseUrl/api/v1/files';

      // 构建查询参数
      Map<String, String> params = {
        'section': section,
      };
      if (path.isNotEmpty) {
        params['path'] = path;
      }

      String queryString = params.entries
          .map((e) =>
              '${Uri.encodeComponent(e.key)}=${Uri.encodeComponent(e.value)}')
          .join('&');

      final response = await http.get(
        Uri.parse('$filesUrl?$queryString'),
        headers: <String, String>{
          'Authorization': 'Bearer ${AuthConfig.getUserToken() ?? ''}',
        },
      ).timeout(const Duration(seconds: 30));

      if (response.statusCode == 200) {
        List<dynamic> data = jsonDecode(response.body)['data'];
        return data
            .map((item) => FileInfo(
                  id: item['id'],
                  name: item['name'] as String,
                  section: item['section'] as String,
                  isDirectory: item['isDirectory'] as bool,
                  modified: DateTime.parse(item['modified'] as String),
                  size: item['size'] as int,
                ))
            .toList();
      } else {
        return [];
      }
    } catch (e) {
      print('获取文件列表时出错: $e');
      return [];
    }
  }

  static Future<Map<String, dynamic>> exportFile(String fileId) async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String exportUrl = '$baseUrl/api/v1/files/$fileId/export';

      final response = await http.get(
        Uri.parse(exportUrl),
        headers: <String, String>{
          'Authorization': 'Bearer ${AuthConfig.getUserToken() ?? ''}',
        },
      ).timeout(const Duration(seconds: 30));

      if (response.statusCode == 200) {
        Map<String, dynamic> data = jsonDecode(response.body);
        return data;
      } else {
        return {'code': response.statusCode, 'message': '导出文件失败', 'data': null};
      }
    } catch (e) {
      return {'code': -1, 'message': '网络请求失败', 'data': null};
    }
  }

  static Future<bool> processFileContent(String fileId) async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String processUrl = '$baseUrl/api/v1/files/$fileId/process';

      final response = await http.post(
        Uri.parse(processUrl),
        headers: <String, String>{
          'Authorization': 'Bearer ${AuthConfig.getUserToken() ?? ''}',
        },
      ).timeout(const Duration(seconds: 30));

      return response.statusCode == 200;
    } catch (e) {
      print('处理文件内容时出错: $e');
      return false;
    }
  }

  static Future<Map<String, dynamic>> moveFileToSection(String fileId, String targetSection, [String currentPath = '']) async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String moveUrl = '$baseUrl/api/v1/files/$fileId/move';

      final response = await http.post(
        Uri.parse(moveUrl),
        headers: <String, String>{
          'Content-Type': 'application/json; charset=UTF-8',
          'Authorization': 'Bearer ${AuthConfig.getUserToken() ?? ''}',
        },
        body: jsonEncode(<String, String>{
          'targetSection': targetSection,
          'currentPath': currentPath,
        }),
      ).timeout(const Duration(seconds: 30));

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

  static Future<bool> importFile(String targetSection,
      [String path = '']) async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String importUrl = '$baseUrl/api/v1/files/import';

      final response = await http
          .post(
            Uri.parse(importUrl),
            headers: <String, String>{
              'Content-Type': 'application/json; charset=UTF-8',
              'Authorization': 'Bearer ${AuthConfig.getUserToken() ?? ''}',
            },
            body: jsonEncode(<String, String>{
              'targetSection': targetSection,
              'path': path,
            }),
          )
          .timeout(const Duration(seconds: 30));

      return response.statusCode == 200;
    } catch (e) {
      print('导入文件时出错: $e');
      return false;
    }
  }
}

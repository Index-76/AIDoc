import 'dart:convert';
import 'package:http/http.dart' as http;
import 'dart:io' as io;
import 'package:http_parser/http_parser.dart';
import 'dart:typed_data';
import 'dart:async';
import '../config/server_config.dart';
import '../config/auth_config.dart';
import 'package:universal_html/html.dart' as html;
import 'package:flutter/foundation.dart';

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

      // 根据文件扩展名确定正确的 Content-Type
      String contentType = _getContentTypeForFile(fileName);
      MediaType mediaType = MediaType.parse(contentType);

      print('开始上传文件：$fileName, Content-Type: $contentType');

      // 根据文件类型处理上传
      if (file is io.File) {
        // 原生文件处理
        var fileBytes = await file.readAsBytes();
        print('原生文件大小：${fileBytes.length} bytes');
        var multipartFile = http.MultipartFile.fromBytes(
          'file',
          fileBytes,
          filename: fileName,
          contentType: mediaType,
        );
        request.files.add(multipartFile);
      } else if (file is _VirtualFileForWeb) {
        // Web 环境文件处理
        var fileBytes = file.readAsBytesSync();
        print('Web 文件大小：${fileBytes.length} bytes');
        var multipartFile = http.MultipartFile.fromBytes(
          'file',
          fileBytes,
          filename: fileName,
          contentType: mediaType,
        );
        request.files.add(multipartFile);
      } else if (file != null && file.bytes != null) {
        // 添加对 PlatformFile 的支持
        print('PlatformFile 大小：${file.bytes!.length} bytes');
        var multipartFile = http.MultipartFile.fromBytes(
          'file',
          file.bytes!,
          filename: fileName,
          contentType: mediaType,
        );
        request.files.add(multipartFile);
      } else {
        return {'code': -1, 'message': '文件数据无效', 'data': null};
      }

      if (section.isNotEmpty) {
        request.fields['section'] = section;
      } else {
        request.fields['section'] = 'read';
      }

      print('发送上传请求到：$uploadUrl');
      var response = await request.send();
      print('收到响应，状态码：${response.statusCode}');
      var responseString = await response.stream.bytesToString();
      print('响应内容：$responseString');

      if (response.statusCode == 200) {
        Map<String, dynamic> data = jsonDecode(responseString);
        return data;
      } else {
        return {
          'code': response.statusCode,
          'message': '上传文件失败：状态码 ${response.statusCode}',
          'data': null
        };
      }
    } catch (e) {
      print('上传异常：$e');
      return {'code': -1, 'message': '网络请求失败：$e', 'data': null};
    }
  }

  static Future<Map<String, dynamic>> deleteFile(String fileId) async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String deleteUrl = '$baseUrl/api/v1/files/$fileId/delete';

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

      // 使用kIsWeb检测是否为Web平台
      if (kIsWeb) {
        // 创建XMLHttpRequest对象来处理二进制数据
        final xhr = html.HttpRequest();
        xhr.responseType = 'blob'; // 设置响应类型为blob以正确处理二进制数据

        Completer<Map<String, dynamic>> completer = Completer();

        xhr.onLoad.listen((event) {
          if (xhr.status == 200) {
            final blob = html.Blob([xhr.response]);

            // 从响应头获取文件名，如果有的话
            String? contentDisposition =
                xhr.getResponseHeader('Content-Disposition');
            String filename = 'downloaded_file';

            if (contentDisposition != null) {
              // 从Content-Disposition头提取文件名
              RegExp exp = RegExp(r"filename\*?=UTF-8''([^;]+)");
              Match? match = exp.firstMatch(contentDisposition);

              if (match != null) {
                // 解码URL编码的文件名
                String encodedFilename = match.group(1)?.trim() ?? '';
                try {
                  filename = Uri.decodeComponent(encodedFilename);
                } catch (e) {
                  // 如果解码失败，使用原始编码名称
                  filename = encodedFilename;
                }
              } else {
                // 如果没有找到UTF-8编码的文件名，尝试普通的filename参数
                exp = RegExp(r'filename=([^;]+)');
                match = exp.firstMatch(contentDisposition);
                if (match != null) {
                  filename = match.group(1)?.trim() ?? filename;
                  // 去掉可能的引号
                  filename = filename.replaceAll(RegExp(r'^"|"$'), '');
                }
              }
            }

            // 创建临时链接并下载
            final url = html.Url.createObjectUrl(blob);
            final anchor = html.AnchorElement()
              ..href = url
              ..style.display = 'none'
              ..download = filename;

            html.document.body!.children.add(anchor);
            anchor.click();
            html.document.body!.children.remove(anchor);
            html.Url.revokeObjectUrl(url);

            completer.complete({'code': 200, 'message': '下载成功'});
          } else {
            completer.complete({'code': xhr.status ?? 500, 'message': '下载失败'});
          }
        });

        xhr.onError.listen((event) {
          completer.complete({'code': 500, 'message': '网络错误'});
        });

        xhr.open('GET', downloadUrl);
        xhr.setRequestHeader(
            'Authorization', 'Bearer ${AuthConfig.getUserToken() ?? ''}');
        xhr.send();

        return await completer.future;
      } else {
        // 在原生环境中，获取文件数据
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
          return {
            'code': response.statusCode,
            'message': '下载文件失败',
            'data': null
          };
        }
      }
    } catch (e) {
      return {'code': -1, 'message': '网络请求失败: $e', 'data': null};
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

  static Future<Map<String, dynamic>> sendChatMessage(String message,
      [String? sessionId]) async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String chatUrl = '$baseUrl/api/v1/chat/message';

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

  static Future<Map<String, dynamic>> getChatSessions() async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String sessionsUrl = '$baseUrl/api/v1/chat/sessionList';

      final response = await http.get(
        Uri.parse(sessionsUrl),
        headers: <String, String>{
          'Authorization': 'Bearer ${AuthConfig.getUserToken() ?? ''}',
        },
      ).timeout(const Duration(seconds: 30));

      if (response.statusCode == 200) {
        Map<String, dynamic> data = jsonDecode(response.body);
        if (data['code'] == 200 &&
            data['data'] != null &&
            data['data'] is List) {
          List<String> sessionIds = List<String>.from(data['data']);
          List<Map<String, dynamic>> sessions = [];
          for (String sessionId in sessionIds) {
            sessions.add({
              'id': sessionId,
              'title': '对话 $sessionId',
            });
          }
          return {'code': 200, 'message': 'success', 'sessions': sessions};
        } else {
          return data;
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

  static Future<Map<String, dynamic>> getChatHistoryBySessionId(
      String sessionId) async {
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

  static Future<Map<String, dynamic>> deleteChatSession(
      String sessionId) async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String deleteUrl = '$baseUrl/api/v1/chat/session/$sessionId/delete';

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
        return {'code': response.statusCode, 'message': '删除对话失败', 'data': null};
      }
    } catch (e) {
      return {'code': -1, 'message': '网络请求失败', 'data': null};
    }
  }

  static Future<Map<String, dynamic>> getChatDetail(String chatId) async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String chatDetailUrl = '$baseUrl/api/v1/chat/$chatId';

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

  static Future<Map<String, dynamic>> getChatHistoryBySession(
      String sessionId) async {
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
        if (data['code'] == 200 &&
            data['data'] != null &&
            data['data'] is List) {
          List<dynamic> rawMessages = data['data'];

          List<Map<String, dynamic>> formattedMessages = [];
          for (var msg in rawMessages) {
            formattedMessages.add({
              'senderType': msg['senderType'],
              'content': msg['content'],
            });
          }

          return {'code': 200, 'message': 'success', 'data': formattedMessages};
        } else {
          return data;
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

  static Future<Map<String, dynamic>> updateChatTitle(
      String chatId, String newTitle) async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String chatTitleUrl = '$baseUrl/api/v1/chat/$chatId/title';

      final response = await http
          .put(
            Uri.parse(chatTitleUrl),
            headers: <String, String>{
              'Content-Type': 'application/json; charset=UTF-8',
              'Authorization': 'Bearer ${AuthConfig.getUserToken() ?? ''}',
            },
            body: jsonEncode({'title': newTitle}),
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

  // 清理缓存 API
  static Future<Map<String, dynamic>> cleanCache(String cleanContent) async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String cleanUrl = '$baseUrl/api/v1/auth/clean';

      final response = await http
          .post(
            Uri.parse(cleanUrl),
            headers: <String, String>{
              'Content-Type': 'application/json; charset=UTF-8',
              'Authorization': 'Bearer ${AuthConfig.getUserToken() ?? ''}',
            },
            body: jsonEncode(<String, String>{
              'cleanContent': cleanContent,
            }),
          )
          .timeout(const Duration(seconds: 30));

      if (response.statusCode == 200) {
        Map<String, dynamic> data = jsonDecode(response.body);
        return data;
      } else {
        return {
          'code': response.statusCode,
          'message': '清理缓存失败',
          'data': null
        };
      }
    } catch (e) {
      return {'code': -1, 'message': '网络请求失败', 'data': null};
    }
  }

  // 工具功能 API
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
        return {'code': response.statusCode, 'message': '获取文档失败', 'data': null};
      }
    } catch (e) {
      return {'code': -1, 'message': '网络请求失败', 'data': null};
    }
  }

  static Future<Map<String, dynamic>> queryDatabase(
      Map<String, dynamic> queryData) async {
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
            body: jsonEncode(queryData),
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

  static Future<Map<String, dynamic>> summarizeContent(
      Map<String, dynamic> contentData) async {
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
            body: jsonEncode(contentData),
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

  static Future<Map<String, dynamic>> convertFormat(
      Map<String, dynamic> formatData) async {
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
            body: jsonEncode(formatData),
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
      Map<String, dynamic> formData) async {
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
            body: jsonEncode(formData),
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

  static Future<Map<String, dynamic>> insertToDatabase(
      Map<String, dynamic> insertData) async {
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
            body: jsonEncode(insertData),
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

  /// 建立SSE连接，返回原始行流
  static Future<Stream<String>?> connectSSE(String sessionId) async {
    try {
      debugPrint('🚀 开始SSE连接，会话ID: $sessionId');
      final baseUrl = ServerConfig.baseUrl;
      final connectUrl = '$baseUrl/api/v1/sse/connect/$sessionId';

      final request = http.Request('GET', Uri.parse(connectUrl));
      request.headers['Authorization'] =
          'Bearer ${AuthConfig.getUserToken() ?? ''}';
      request.headers['Accept'] = 'text/event-stream';
      request.headers['Cache-Control'] = 'no-cache';

      final response = await request.send();
      debugPrint('📡 SSE响应状态码: ${response.statusCode}');

      if (response.statusCode == 200) {
        final stream = response.stream
            .transform(utf8.decoder)
            .transform(const LineSplitter())
            .asBroadcastStream();

        // 可选内部监听，便于调试
        stream.listen((line) {
          debugPrint('🔊 内部行监听: $line');
        });

        debugPrint('✅ SSE连接建立成功，返回原始行流');
        return stream;
      } else {
        debugPrint('❌ SSE连接失败: 状态码 ${response.statusCode}');
        return null;
      }
    } catch (e) {
      debugPrint('💥 SSE连接异常: $e');
      return null;
    }
  }

  /// SSE断开连接，返回是否成功
  static Future<bool> disconnectSSE(String sessionId) async {
    try {
      final baseUrl = ServerConfig.baseUrl;
      final disconnectUrl = '$baseUrl/api/v1/sse/disconnect/$sessionId';

      final response = await http.post(
        Uri.parse(disconnectUrl),
        headers: {
          'Content-Type': 'application/json; charset=UTF-8',
          'Authorization': 'Bearer ${AuthConfig.getUserToken() ?? ''}',
        },
      ).timeout(const Duration(seconds: 30));

      return response.statusCode == 200;
    } catch (e) {
      debugPrint('SSE断开连接异常: $e');
      return false;
    }
  }

  /// 获取SSE连接数，返回整数，出错返回 -1
  static Future<int> getSSEConnectionsCount() async {
    try {
      final baseUrl = ServerConfig.baseUrl;
      final connectionsUrl = '$baseUrl/api/v1/sse/connections';

      final response = await http.get(
        Uri.parse(connectionsUrl),
        headers: {
          'Authorization': 'Bearer ${AuthConfig.getUserToken() ?? ''}',
        },
      ).timeout(const Duration(seconds: 30));

      if (response.statusCode == 200) {
        // 根据文档，接口直接返回数字字符串，如 "1"
        final body = response.body.trim();
        return int.tryParse(body) ?? -1;
      } else {
        return -1;
      }
    } catch (e) {
      debugPrint('获取SSE连接数异常: $e');
      return -1;
    }
  }

  /// 验证用户token是否有效
  static Future<bool> validateToken() async {
    try {
      String? token = AuthConfig.getUserToken();
      if (token == null || token.isEmpty) {
        debugPrint('validateToken: Token为空');
        return false;
      }

      // 构建完整的API URL
      String baseUrl = ServerConfig.baseUrl;
      // 如果baseUrl为空（Web环境），使用相对路径
      String apiUrl =
          baseUrl.isEmpty ? '/api/v1/auth/me' : '$baseUrl/api/v1/auth/me';
      final url = Uri.parse(apiUrl);

      debugPrint('validateToken: 请求URL - $url');

      final response = await http.get(
        url,
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer $token',
        },
      );

      debugPrint('validateToken响应状态: ${response.statusCode}');
      debugPrint('validateToken响应内容: ${response.body}');

      if (response.statusCode == 200) {
        final jsonResponse = json.decode(response.body);
        if (jsonResponse is Map<String, dynamic>) {
          int code = jsonResponse['code'] is int ? jsonResponse['code'] : 0;
          var data = jsonResponse['data'];

          if (code == 200 && data != null) {
            debugPrint('validateToken: Token验证成功');
            return true;
          } else {
            String errorMsg =
                jsonResponse['msg'] ?? jsonResponse['message'] ?? '未知错误';
            debugPrint('validateToken: Token验证失败 - $errorMsg');
            return false;
          }
        } else {
          debugPrint('validateToken: 响应格式错误');
          return false;
        }
      } else if (response.statusCode == 401) {
        debugPrint('validateToken: Token无效或已过期');
        return false;
      } else {
        debugPrint('validateToken: 请求失败 - 状态码: ${response.statusCode}');
        return false;
      }
    } catch (e) {
      debugPrint('validateToken异常: $e');
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

  static Future<Map<String, dynamic>> updateUserConfig(
      Map<String, dynamic> configData) async {
    try {
      String baseUrl = ServerConfig.baseUrl;
      String configUrl = '$baseUrl/api/v1/user-config';

      final response = await http
          .post(
            Uri.parse(configUrl),
            headers: <String, String>{
              'Content-Type': 'application/json; charset=UTF-8',
              'Authorization': 'Bearer ${AuthConfig.getUserToken() ?? ''}',
            },
            body: jsonEncode(configData),
          )
          .timeout(const Duration(seconds: 30));

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

    final Map<String, dynamic> responseData =
        data['data'] as Map<String, dynamic>;

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

  /// 根据文件扩展名获取正确的 Content-Type
  static String _getContentTypeForFile(String fileName) {
    if (fileName.isEmpty) {
      return 'application/octet-stream';
    }

    final lowerFileName = fileName.toLowerCase();
    
    // Word 文档
    if (lowerFileName.endsWith('.doc')) {
      return 'application/msword';
    } else if (lowerFileName.endsWith('.docx')) {
      return 'application/vnd.openxmlformats-officedocument.wordprocessingml.document';
    }
    // Excel 表格
    else if (lowerFileName.endsWith('.xls')) {
      return 'application/vnd.ms-excel';
    } else if (lowerFileName.endsWith('.xlsx')) {
      return 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
    }
    // PDF 文档
    else if (lowerFileName.endsWith('.pdf')) {
      return 'application/pdf';
    }
    // Markdown 文件
    else if (lowerFileName.endsWith('.md') || lowerFileName.endsWith('.markdown')) {
      return 'text/markdown';
    }
    // 文本文件
    else if (lowerFileName.endsWith('.txt')) {
      return 'text/plain';
    }
    // 图片文件
    else if (lowerFileName.endsWith('.jpg') || lowerFileName.endsWith('.jpeg')) {
      return 'image/jpeg';
    } else if (lowerFileName.endsWith('.png')) {
      return 'image/png';
    } else if (lowerFileName.endsWith('.gif')) {
      return 'image/gif';
    }
    // HTML 文件
    else if (lowerFileName.endsWith('.html') || lowerFileName.endsWith('.htm')) {
      return 'text/html';
    }
    // JSON 文件
    else if (lowerFileName.endsWith('.json')) {
      return 'application/json';
    }
    // 默认返回 octet-stream
    else {
      return 'application/octet-stream';
    }
  }
}

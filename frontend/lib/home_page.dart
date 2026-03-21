import 'package:flutter/material.dart';
import 'dart:async';
import 'dart:convert';
import 'package:logger/logger.dart';
import 'services/api_service.dart';
import 'models/chat_message.dart';
import 'widgets/tips.dart';
import 'login_page.dart';
import 'widgets/file_section.dart';
import 'widgets/chat_message_item.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:flutter/services.dart' show rootBundle;
import 'package:flutter_markdown/flutter_markdown.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'AIDoc智能文档处理平台',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
        useMaterial3: true,
      ),
      home: const AuthWrapper(),
    );
  }
}

class AuthWrapper extends StatefulWidget {
  const AuthWrapper({super.key});

  @override
  State<AuthWrapper> createState() => _AuthWrapperState();
}

class _AuthWrapperState extends State<AuthWrapper> {
  bool _isCheckingAuth = true;
  bool _isAuthenticated = false;

  @override
  void initState() {
    super.initState();
    _checkAuthStatus();
  }

  Future<void> _checkAuthStatus() async {
    try {
      final isValid = await ApiService.validateToken();
      if (mounted) {
        setState(() {
          _isCheckingAuth = false;
          _isAuthenticated = isValid;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _isCheckingAuth = false;
          _isAuthenticated = false;
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

    return _isAuthenticated ? const HomePage() : const LoginPage();
  }
}

class HomePage extends StatelessWidget {
  const HomePage({super.key});

  @override
  Widget build(BuildContext context) {
    return TooltipOverlay(
      child: const MyHomePage(),
    );
  }
}

class MyHomePage extends StatefulWidget {
  const MyHomePage({super.key});

  @override
  State<MyHomePage> createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  final List<ChatMessage> _messages = [];
  final ScrollController _scrollController = ScrollController();
  final TextEditingController _textController = TextEditingController();
  final FocusNode _textFieldFocusNode = FocusNode();
  final Logger _logger = Logger();

  final GlobalKey<FileSectionState> _waitingSectionKey = GlobalKey();
  final GlobalKey<FileSectionState> _readSectionKey = GlobalKey();
  final GlobalKey<FileSectionState> _templateSectionKey = GlobalKey();
  final GlobalKey<FileSectionState> _resultSectionKey = GlobalKey();

  String _currentDocumentContent = '';

  String? _currentSessionId;

  List<Map<String, dynamic>> _chatSessions = [];

  int _chatSessionListVersion = 0;

  bool _isCreatingChat = false;
  bool _isDeletingChat = false;
  bool _isInitializing = false;
  bool _hasBeenInitialized = false;
  bool _isLoading = false;

  // 添加菜单加载状态变量
  bool _isLoadingMenu = false;
  bool _isLoadingSessions = false; // 防止并发加载会话列表

  // 应用级别的初始化锁
  static bool _appLevelInitLock = false;

  // SSE 连接数限制
  static const int _maxSseConnections = 3;

  // SSE 相关变量
  String? _toolStatusText; // 当前工具提示文本
  String? _lastToolName; // 记录最后一次使用的工具名称
  int? _lastToolValue; // 记录最后一次使用的工具值（用于判断是否需要刷新结果区）
  Map<String, StreamSubscription<String>> _sseSubscriptions = {};
  List<String> _sseConnectionOrder = []; // 按连接时间顺序存储会话 ID，最早的在前面
  bool _isSseConnected = false; // SSE 连接状态

  String? _pendingSwitchSessionId; // 当前正在处理的切换目标

  @override
  void initState() {
    super.initState();

    // 初始化各个区域的状态
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _smartInitializeChat(); // 使用初始化：优先复用最新空对话
    });
  }

  // 加载当前对话的历史记录
  Future<bool> _loadChatHistoryForCurrentSession() async {
    if (_currentSessionId == null) return false;

    try {
      final response =
          await ApiService.getChatHistoryBySession(_currentSessionId!);

      if (response['code'] == 200) {
        List<dynamic> chatMessages = response['data'] ?? [];

        if (mounted) {
          setState(() {
            _messages.clear();
            // 新增：重置 loading 状态
            _isLoading = false;
            _toolStatusText = null;
            // 添加消息到列表
            for (var msg in chatMessages) {
              String senderType = msg['senderType'];
              String content = msg['content'];

              if (senderType == 'USER') {
                _messages.add(ChatMessage.user(content));
              } else if (senderType == 'AI') {
                _messages.add(ChatMessage.ai(content));
              }
            }
          });
          _scrollToBottom();
        }
        return true;
      } else {
        _logger.w('获取对话历史失败: ${response['msg'] ?? response['message']}');
        if (mounted) {
          TooltipUtil.showTooltip('加载对话历史失败，请重试', TooltipPosition.windowCenter);
        }
        return false;
      }
    } catch (e) {
      _logger.w('获取对话历史时发生错误: $e');
      if (mounted) {
        TooltipUtil.showTooltip('加载对话历史异常', TooltipPosition.windowCenter);
      }
      return false;
    }
  }

  // 滚动到最新消息
  void _scrollToBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scrollController.hasClients) {
        _scrollController.animateTo(
          _scrollController.position.maxScrollExtent,
          duration: const Duration(milliseconds: 300),
          curve: Curves.easeOut,
        );
      }
    });
  }

  // 创建新对话（新增 showTip 参数，控制是否显示成功提示）
  Future<void> _createNewChat({bool showTip = true}) async {
    // 防止重复创建
    if (_isCreatingChat) {
      _logger.d('正在创建对话中，跳过重复创建');
      return;
    }

    setState(() {
      _isCreatingChat = true;
    });

    try {
      // 检查当前会话是否有新内容，如果没有新内容则不创建
      if (_currentSessionId != null && _messages.isNotEmpty) {
        // 检查是否只有欢迎消息（通常只有一条 AI 消息）
        bool hasOnlyWelcomeMessage =
            _messages.length == 1 && !_messages[0].isUser;

        // 如果没有用户发送的消息，则认为没有新内容
        if (hasOnlyWelcomeMessage) {
          if (mounted && showTip) {
            TooltipUtil.showTooltip(
              '当前对话没有新内容，无需创建新对话',
              TooltipPosition.windowCenter,
            );
          }
          return;
        }
      }

      // 刷新对话列表，获取最新会话列表
      await _loadChatSessions();

      // 如果对话列表为空，直接创建新对话
      if (_chatSessions.isEmpty) {
        await _doCreateNewChatInternal(showTip: showTip);
        return;
      }

      // 获取最近一条对话（列表中最后一个）
      final latestSession = _chatSessions.last;
      final latestSessionId = latestSession['id'];

      // 如果最近一条对话不是当前对话，检查它是否为空对话
      if (latestSessionId != _currentSessionId) {
        // 获取该会话的历史记录，判断是否为空对话（只有一条 AI 消息，无用户消息）
        final historyResponse =
            await ApiService.getChatHistoryBySession(latestSessionId);
        bool isEmptyChat = false;
        if (historyResponse['code'] == 200) {
          List<dynamic> chatHistory = historyResponse['data'] ?? [];
          isEmptyChat = chatHistory.isEmpty ||
              (chatHistory.length == 1 && chatHistory[0]['senderType'] != 'USER');
        }
        if (isEmptyChat) {
          // 切换到最近的空对话，不显示内部切换提示
          _switchToChat(latestSessionId, showTip: false);
          if (showTip && mounted) {
            TooltipUtil.showTooltip('已复用最近空对话', TooltipPosition.windowCenter);
          }
          return;
        }
      }

      // 否则创建新对话
      await _doCreateNewChatInternal(showTip: showTip);
    } finally {
      if (mounted) {
        setState(() {
          _isCreatingChat = false;
        });
      }
    }
  }

  // 实际创建新对话的核心逻辑（不处理 _isCreatingChat 标志，由外层统一控制）
  Future<void> _doCreateNewChatInternal({bool showTip = true}) async {
    try {
      final response = await ApiService.createNewChat();

      if (response['code'] == 200 && response['data'] != null) {
        String newSessionId = response['data']['sessionId'];
        final String? previousSessionId = _currentSessionId; // 保存旧会话 ID

        if (mounted) {
          // 根据 showTip 决定是否显示提示
          if (showTip) {
            TooltipUtil.showTooltip('新对话已创建', TooltipPosition.windowCenter);
          }

          setState(() {
            _currentSessionId = newSessionId;
            _messages.clear();
            _currentDocumentContent = '';
          });
          await _loadChatHistoryForCurrentSession();
          await _loadChatSessions();

          // 断开旧会话的 SSE 连接
          if (previousSessionId != null && previousSessionId != newSessionId) {
            await _disconnectSSE(previousSessionId);
          }

          // 连接新会话的 SSE
          await _connectSSE(newSessionId);

          // 保存当前会话 ID 到本地存储
          SharedPreferences prefs = await SharedPreferences.getInstance();
          await prefs.setString('last_session_id', newSessionId);
        }
      } else {
        _showErrorTooltip('创建新对话失败：${response['msg'] ?? response['message']}');
      }
    } catch (e) {
      _showErrorTooltip('创建新对话时发生错误：$e');
    }
  }

  // 加载对话历史 - 统一的基础刷新方法
  Future<void> _loadChatSessions() async {
    // 防止并发加载
    if (_isLoadingSessions) {
      _logger.d('会话列表正在加载中，跳过重复加载');
      return;
    }

    setState(() {
      _isLoadingSessions = true;
    });

    try {
      final response = await ApiService.getChatSessions();

      if (response['code'] == 200) {
        List<dynamic> sessions = response['sessions'] ?? [];

        if (mounted) {
          setState(() {
            _chatSessions = List<Map<String, dynamic>>.from(sessions);
            _chatSessionListVersion++;
          });

          // 校验当前会话是否在列表中
          if (_currentSessionId != null) {
            bool exists =
                _chatSessions.any((s) => s['id'] == _currentSessionId);
            if (!exists) {
              // 查找最近的空对话
              String? emptySessionId;
              for (var session in _chatSessions) {
                String sessionId = session['id'];
                final historyResponse =
                    await ApiService.getChatHistoryBySession(sessionId);
                if (historyResponse['code'] == 200) {
                  List<dynamic> chatHistory = historyResponse['data'] ?? [];
                  bool isEmptyChat = chatHistory.isEmpty ||
                      (chatHistory.length == 1 &&
                          chatHistory[0]['senderType'] != 'USER');

                  if (isEmptyChat) {
                    emptySessionId = sessionId;
                    break;
                  }
                }
              }

              if (emptySessionId != null) {
                // 切换到最近的空对话
                _switchToChat(emptySessionId);
              } else if (_chatSessions.isNotEmpty) {
                // 没有空对话，切换到第一个会话
                _switchToChat(_chatSessions.first['id']);
              } else {
                // 列表为空，创建新会话
                _createNewChat();
              }
            }
          }
        }
      } else {
        _showErrorTooltip(
            '获取对话历史失败: ${response['msg'] ?? response['message']}');
      }
    } catch (e) {
      _showErrorTooltip('获取对话历史时发生错误: $e');
    } finally {
      if (mounted) {
        setState(() {
          _isLoadingSessions = false;
        });
      }
    }
  }

  // 切换到指定对话
  void _switchToChat(String sessionId, {bool showTip = true}) async {
    // 如果点击的是当前对话，不执行切换
    if (sessionId == _currentSessionId) {
      if (mounted) {
        TooltipUtil.showTooltip('已在当前对话中', TooltipPosition.windowCenter);
      }
      return;
    }

    _pendingSwitchSessionId = sessionId;
    final String? previousSessionId = _currentSessionId; // 保存旧会话 ID

    try {
      final response = await ApiService.getChatHistoryBySession(sessionId);
      // 如果已经被更新的切换覆盖，则忽略结果
      if (_pendingSwitchSessionId != sessionId) return;

      if (response['code'] == 200) {
        List<dynamic> chatMessages = response['data'] ?? [];

        if (mounted) {
          // 只在需要时显示切换提示
          if (showTip) {
            TooltipUtil.showTooltip('已切换到对话', TooltipPosition.windowCenter);
          }

          setState(() {
            _currentSessionId = sessionId;
            _messages.clear();
            // 添加消息到列表
            for (var msg in chatMessages) {
              String senderType = msg['senderType'];
              String content = msg['content'];

              if (senderType == 'USER') {
                _messages.add(ChatMessage.user(content));
              } else if (senderType == 'AI') {
                _messages.add(ChatMessage.ai(content));
              }
            }
          });

          // 断开旧会话的 SSE 连接
          if (previousSessionId != null && previousSessionId != sessionId) {
            await _disconnectSSE(previousSessionId);
          }

          // 连接新会话的 SSE
          await _connectSSE(sessionId);

          // 在添加完消息后，滚动到底部显示最新消息
          WidgetsBinding.instance.addPostFrameCallback((_) {
            if (_scrollController.hasClients) {
              _scrollController.animateTo(
                _scrollController.position.maxScrollExtent,
                duration: const Duration(milliseconds: 300),
                curve: Curves.easeOut,
              );
            }
          });

          // 保存当前会话 ID 到 SharedPreferences
          final prefs = await SharedPreferences.getInstance();
          await prefs.setString('last_session_id', sessionId);
        }
      } else {
        if (_pendingSwitchSessionId == sessionId) {
          _showErrorTooltip(
              '获取对话历史失败：${response['msg'] ?? response['message']}');
        }
      }
    } catch (e) {
      if (_pendingSwitchSessionId == sessionId) {
        _showErrorTooltip('获取对话历史时发生错误：$e');
      }
    } finally {
      if (_pendingSwitchSessionId == sessionId) {
        if (mounted) {
          setState(() {
            _pendingSwitchSessionId = null;
          });
        }
      }
    }
  }

  // 删除当前对话的统一处理逻辑（不再显示内部切换/创建提示）
  Future<void> _handleDeleteCurrentChat() async {
    // 删除成功后，立即清理本地状态
    if (mounted) {
      setState(() {
        _currentSessionId = null;
        _messages.clear();
        _currentDocumentContent = '';
      });
    }

    // 刷新对话历史列表
    await _loadChatSessions();

    // 判断是否是最后一个对话
    if (_chatSessions.isEmpty) {
      // 如果是最后一个对话，创建新对话（不显示内部提示）
      await _createNewChat(showTip: false);
      if (mounted) {
        TooltipUtil.showTooltip(
            '已删除最后一个对话，正在创建新对话...', TooltipPosition.windowCenter);
      }
    } else {
      // 如果不是最后一个对话，切换到最近的对话（不显示内部提示）
      String recentSessionId = _chatSessions.last['id'];
      _switchToChat(recentSessionId, showTip: false);  // 关键修改：抑制"已切换到对话"
      if (mounted) {
        TooltipUtil.showTooltip(
            '已删除当前对话，已切换到最近的对话', TooltipPosition.windowCenter);
      }
    }
  }

  // 删除当前对话
  void _deleteCurrentChat() async {
    if (_currentSessionId == null || _isDeletingChat) {
      _showErrorTooltip('没有可删除的对话或正在删除中');
      return;
    }

    setState(() {
      _isDeletingChat = true;
    });

    try {
      final response = await ApiService.deleteChatSession(_currentSessionId!);
      if (response['code'] == 200) {
        // 使用统一的删除处理逻辑
        await _handleDeleteCurrentChat();
      } else {
        _showErrorTooltip('删除对话失败: ${response['message']}');
      }
    } catch (e) {
      _showErrorTooltip('删除对话时发生错误: $e');
    } finally {
      if (mounted) {
        setState(() {
          _isDeletingChat = false;
        });
      }
    }
  }

  // 显示错误提示
  void _showErrorTooltip(String message) {
    if (mounted) {
      TooltipUtil.showTooltip(message, TooltipPosition.windowCenter);
    }
  }

  Future<void> _connectSSE(String sessionId) async {
    // 如果已有该会话的连接，先取消（防止重复）
    if (_sseSubscriptions.containsKey(sessionId)) {
      await _sseSubscriptions[sessionId]!.cancel();
      _sseSubscriptions.remove(sessionId);
      _sseConnectionOrder.remove(sessionId);
    }

    if (!mounted) return;

    final stream = await ApiService.connectSSE(sessionId);
    if (stream == null) {
      return;
    }

    final subscription = stream.listen(
      (line) {
        if (line.startsWith('data:')) {
          final jsonStr = line.substring(5).trim();
          try {
            final event = jsonDecode(jsonStr) as Map<String, dynamic>;
            _handleSseEvent(event);
          } catch (e) {
            // JSON解析失败时忽略
          }
        }
      },
      onError: (error) {
        // 连接出错时从映射中移除该订阅
        _sseSubscriptions.remove(sessionId);
        _sseConnectionOrder.remove(sessionId);
        if (_sseSubscriptions.isEmpty) {
          _isSseConnected = false;
        }
      },
      onDone: () {
        // 连接关闭时从映射中移除
        _sseSubscriptions.remove(sessionId);
        _sseConnectionOrder.remove(sessionId);
        if (_sseSubscriptions.isEmpty) {
          _isSseConnected = false;
        }
      },
    );

    _sseSubscriptions[sessionId] = subscription;
    _sseConnectionOrder.add(sessionId);
    _isSseConnected = true;
  }

  Future<void> _disconnectSSE([String? sessionId]) async {
    if (sessionId != null) {
      // 断开指定会话
      final subscription = _sseSubscriptions.remove(sessionId);
      if (subscription != null) {
        await subscription.cancel();
        await ApiService.disconnectSSE(sessionId);
      }
      _sseConnectionOrder.remove(sessionId);
    } else {
      // 断开所有会话
      for (var entry in _sseSubscriptions.entries) {
        await entry.value.cancel();
        await ApiService.disconnectSSE(entry.key);
      }
      _sseSubscriptions.clear();
      _sseConnectionOrder.clear();
    }

    if (_sseSubscriptions.isEmpty) {
      _isSseConnected = false;
    }
  }

  void _handleSseEvent(Map<String, dynamic> event) {
    final String eventType = event['eventType'];
    final String sessionId = event['sessionId'];
    if (sessionId != _currentSessionId) return;

    switch (eventType) {
      case 'tool_begin':
        _handleToolBegin(event);
        break;
      case 'ai_reply_finish':
        _handleAiReplyFinish(event);
        break;
      default:
        break;
    }
  }

  void _handleToolBegin(Map<String, dynamic> event) {
    final int toolValue = event['data'] as int;
    String toolMessage;
    String toolName;
    if (toolValue == 0) {
      toolMessage = '等待回复中...';
      toolName = '等待';
    } else {
      const toolNames = {
        1: '目录查看',
        2: '内容总结',
        3: '格式转换',
        4: '智能填表',
        5: '智能修改',
      };
      toolName = toolNames[toolValue] ?? '未知工具';
      toolMessage = '正在使用 $toolName 工具...';
    }
    if (mounted) {
      setState(() {
        _toolStatusText = toolMessage;
        _lastToolName = toolName;
        _lastToolValue = toolValue;
      });
    }
  }

  void _handleAiReplyFinish(Map<String, dynamic> event) async {
    if (mounted) {
      setState(() {
        _isLoading = false;
        _toolStatusText = null;
      });

      // 添加 system 消息
      if (_lastToolName != null && _lastToolName != '等待') {
        final systemMessage = ChatMessage.system('已执行 $_lastToolName 操作');
        setState(() {
          _messages.add(systemMessage);
          _lastToolName = null;
        });
        _scrollToBottom();
      }
    }
    await _loadLatestAiMessage();

    // 如果工具是 3~5（格式转换、智能填表、智能修改），刷新结果区文件
    if (_lastToolValue != null &&
        _lastToolValue! >= 3 &&
        _lastToolValue! <= 5) {
      await _refreshResultSection();
    }
  }

  Future<void> _loadLatestAiMessage() async {
    if (_currentSessionId == null) return;
    try {
      final response =
          await ApiService.getChatHistoryBySession(_currentSessionId!);
      if (response['code'] == 200) {
        final List<dynamic> chatMessages = response['data'] ?? [];
        final lastAiMsg = chatMessages.lastWhere(
          (msg) => msg['senderType'] == 'AI',
          orElse: () => null,
        );
        if (lastAiMsg != null && mounted) {
          setState(() {
            _messages.add(ChatMessage.ai(lastAiMsg['content']));
          });
          _scrollToBottom();
        }
      }
    } catch (e) {
      // 加载失败时忽略错误
    }
  }

  // 发送消息
  Future<void> _sendMessage(String text) async {
    if (text.isEmpty || _currentSessionId == null) {
      _logger.d('发送消息被阻止：文本为空或无会话ID');
      return;
    }

    _logger.d('开始发送消息: $text');
    _scrollToBottom();

    setState(() {
      _messages.add(ChatMessage.user(text));
      _textController.clear();
      _isLoading = true;
      _toolStatusText = '等待回复中...';
    });

    try {
      final response =
          await ApiService.sendChatMessage(text, _currentSessionId);
      if (response['code'] != 200) {
        throw Exception('发送消息失败: ${response['msg']}');
      }
    } catch (e) {
      _logger.e('发送消息时发生异常: $e');
      if (mounted) {
        setState(() {
          _messages.add(ChatMessage.ai('消息发送失败，请重试。'));
          _isLoading = false;
          _toolStatusText = null;
        });
        _scrollToBottom();
      }
    }

    // 焦点回到输入框
    if (mounted) {
      FocusScope.of(context).requestFocus(_textFieldFocusNode);
    }
  }

  void _refreshAllSections() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        Future.microtask(() {
          if (mounted) {
            [
              _waitingSectionKey,
              _readSectionKey,
              _templateSectionKey,
              _resultSectionKey,
            ]
                .map((key) => key.currentState)
                .whereType<FileSectionState?>()
                .where((state) => state != null)
                .forEach((state) {
              if (mounted) {
                state!.refreshFiles();
              }
            });
          }
        });
      }
    });
  }

  // 清除文件缓存
  Future<void> _clearFileCache() async {
    try {
      // 调用后端 API 清理临时文件
      final result = await ApiService.cleanCache('file');

      if (result['code'] == 200) {
        // 清除前端所有区域的文件缓存
        [
          _waitingSectionKey,
          _readSectionKey,
          _templateSectionKey,
          _resultSectionKey,
        ]
            .map((key) => key.currentState)
            .whereType<FileSectionState?>()
            .where((state) => state != null)
            .forEach((state) {
          if (mounted) {
            state!.clearCache();
          }
        });

        if (mounted) {
          TooltipUtil.showTooltip('文件缓存已清除', TooltipPosition.windowCenter);
        }
      } else {
        if (mounted) {
          TooltipUtil.showTooltip(
            result['message'] ?? '清除文件缓存失败',
            TooltipPosition.windowCenter,
          );
        }
      }
    } catch (e) {
      if (mounted) {
        TooltipUtil.showTooltip(
          '清除文件缓存失败：${e.toString()}',
          TooltipPosition.windowCenter,
        );
      }
    }
  }

  // 登出功能
  Future<void> _logout() async {
    try {
      // 调用后端登出API
      await ApiService.logout();

      // 清除本地存储的认证信息
      await ApiService.clearAuthInfo();

      // 重定向到登录页
      if (mounted) {
        Navigator.of(context).pushAndRemoveUntil(
          MaterialPageRoute(builder: (context) => const LoginPage()),
          (route) => false,
        );
      }
    } catch (e) {
      _logger.e('登出时发生错误: $e');
      // 即使API调用失败，也要清除本地认证信息
      await ApiService.clearAuthInfo();

      if (mounted) {
        Navigator.of(context).pushAndRemoveUntil(
          MaterialPageRoute(builder: (context) => const LoginPage()),
          (route) => false,
        );
      }
    }
  }

  // 修改配置
  void _showConfigDialog() async {
    // 获取当前配置
    final response = await ApiService.getUserConfig();

    Map<String, dynamic> currentConfig = {};
    if (response['code'] == 200 && response['data'] != null) {
      currentConfig = Map<String, dynamic>.from(response['data']);
    } else {
      // 默认配置
      currentConfig = {
        "siliconFlowApiKey": "test",
        "siliconFlowBaseUrl": "https://api.siliconflow.cn/v1/chat/completions",
        "chatModelName": "deepseek-ai/DeepSeek-V3.2",
        "decisionModelName": "Qwen/Qwen2.5-7B-Instruct",
        "analysisModelName": "deepseek-ai/DeepSeek-V3.2"
      };
    }

    // 创建控制器来管理表单输入
    final controllers = {
      'siliconFlowApiKey':
          TextEditingController(text: currentConfig['siliconFlowApiKey']),
      'siliconFlowBaseUrl':
          TextEditingController(text: currentConfig['siliconFlowBaseUrl']),
      'chatModelName':
          TextEditingController(text: currentConfig['chatModelName']),
      'decisionModelName':
          TextEditingController(text: currentConfig['decisionModelName']),
      'analysisModelName':
          TextEditingController(text: currentConfig['analysisModelName']),
    };

    showDialog(
      context: context,
      builder: (BuildContext context) {
        return AlertDialog(
          title: const Text('配置'),
          content: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                TextFormField(
                  controller: controllers['siliconFlowApiKey'],
                  decoration:
                      const InputDecoration(labelText: 'SiliconFlow API Key'),
                  obscureText: true,
                ),
                TextFormField(
                  controller: controllers['siliconFlowBaseUrl'],
                  decoration:
                      const InputDecoration(labelText: 'SiliconFlow Base URL'),
                ),
                TextFormField(
                  controller: controllers['chatModelName'],
                  decoration: const InputDecoration(labelText: '聊天模型名称'),
                ),
                TextFormField(
                  controller: controllers['decisionModelName'],
                  decoration: const InputDecoration(labelText: '决策模型名称'),
                ),
                TextFormField(
                  controller: controllers['analysisModelName'],
                  decoration: const InputDecoration(labelText: '分析模型名称'),
                ),
              ],
            ),
          ),
          actions: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                // 左侧按钮 - 清除配置缓存
                TextButton(
                  onPressed: () async {
                    try {
                      // 调用后端 API 清理过期配置
                      final result = await ApiService.cleanCache('config');

                      if (mounted) {
                        Navigator.of(context).pop(); // 关闭对话框

                        if (result['code'] == 200) {
                          TooltipUtil.showTooltip(
                            '配置缓存已清除',
                            TooltipPosition.windowCenter,
                          );
                        } else {
                          TooltipUtil.showTooltip(
                            result['message'] ?? '清除配置缓存失败',
                            TooltipPosition.windowCenter,
                          );
                        }
                      }
                    } catch (e) {
                      if (mounted) {
                        Navigator.of(context).pop(); // 关闭对话框
                        TooltipUtil.showTooltip(
                          '清除配置缓存失败：${e.toString()}',
                          TooltipPosition.windowCenter,
                        );
                      }
                    }
                  },
                  child: const Text('清除配置缓存'),
                ),
                // 右侧按钮 - 取消和保存
                Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    TextButton(
                      onPressed: () {
                        Navigator.of(context).pop();
                      },
                      child: const Text('取消'),
                    ),
                    TextButton(
                      onPressed: () async {
                        // 更新配置
                        final newConfig = {
                          'siliconFlowApiKey':
                              controllers['siliconFlowApiKey']!.text,
                          'siliconFlowBaseUrl':
                              controllers['siliconFlowBaseUrl']!.text,
                          'chatModelName': controllers['chatModelName']!.text,
                          'decisionModelName':
                              controllers['decisionModelName']!.text,
                          'analysisModelName':
                              controllers['analysisModelName']!.text,
                        };

                        final updateResponse =
                            await ApiService.updateUserConfig(newConfig);

                        if (updateResponse['code'] == 200) {
                          Navigator.of(context).pop();
                          if (mounted) {
                            TooltipUtil.showTooltip(
                                '配置已更新', TooltipPosition.windowCenter);
                          }
                        } else {
                          if (mounted) {
                            TooltipUtil.showTooltip(
                                '配置更新失败：${updateResponse['message']}',
                                TooltipPosition.windowCenter);
                          }
                        }
                      },
                      child: const Text('保存'),
                    ),
                  ],
                ),
              ],
            ),
          ],
        );
      },
    );
  }

  // 显示帮助文档对话框
  void _showHelpDialog() {
    // 读取本地 Markdown 文件
    Future<String> loadHelpContent() async {
      try {
        return await rootBundle.loadString('assets/help.md');
      } catch (e) {
        return '# 帮助文档加载失败\n\n无法找到帮助文档文件，请稍后重试。';
      }
    }

    showDialog(
      context: context,
      builder: (BuildContext context) {
        return AlertDialog(
          title: const Row(
            children: [
              Icon(Icons.help_outline, color: Colors.deepPurple),
              SizedBox(width: 8),
              Text('帮助文档'),
            ],
          ),
          content: ConstrainedBox(
            constraints: BoxConstraints(
              maxWidth: MediaQuery.of(context).size.width *
                  0.56, // 原宽度的 3/4（AlertDialog 默认约 75% 屏幕宽度）
              maxHeight: MediaQuery.of(context).size.height * 0.7,
            ),
            child: SizedBox(
              width: double.maxFinite,
              height: 500,
              child: FutureBuilder<String>(
                future: loadHelpContent(),
                builder: (context, snapshot) {
                  if (snapshot.connectionState == ConnectionState.waiting) {
                    return const Center(child: CircularProgressIndicator());
                  }

                  if (snapshot.hasError) {
                    return Center(
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Icon(Icons.error_outline,
                              size: 48, color: Colors.grey[400]),
                          const SizedBox(height: 16),
                          Text('加载失败：${snapshot.error}'),
                        ],
                      ),
                    );
                  }

                  final content = snapshot.data ?? '帮助文档内容为空';

                  return Markdown(
                    data: content,
                    selectable: true,
                    styleSheet: MarkdownStyleSheet.fromTheme(Theme.of(context))
                        .copyWith(
                      h1: const TextStyle(
                        fontSize: 24,
                        fontWeight: FontWeight.bold,
                        color: Colors.deepPurple,
                      ),
                      h2: const TextStyle(
                        fontSize: 20,
                        fontWeight: FontWeight.bold,
                        color: Colors.deepPurple,
                      ),
                      h3: const TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.bold,
                        color: Colors.deepPurple,
                      ),
                      p: const TextStyle(fontSize: 14, height: 1.6),
                      listBullet: const TextStyle(fontSize: 14),
                      blockquote: TextStyle(
                        fontSize: 14,
                        fontStyle: FontStyle.italic,
                        color: Colors.grey[600],
                      ),
                      code: TextStyle(
                        fontSize: 13,
                        backgroundColor: Colors.grey[200],
                        fontFamily: 'monospace',
                      ),
                      tableBody: const TextStyle(fontSize: 14),
                    ),
                  );
                },
              ),
            ),
          ),
          actions: [
            Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                TextButton(
                  onPressed: () {
                    Navigator.of(context).pop();
                  },
                  style: TextButton.styleFrom(
                    foregroundColor: Colors.grey[700],
                  ),
                  child: const Text('关闭'),
                ),
              ],
            ),
          ],
        );
      },
    );
  }

  // 构建帮助章节
  Widget _buildHelpSection(String title, List<String> items) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 16.0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            style: const TextStyle(
              fontSize: 16,
              fontWeight: FontWeight.bold,
              color: Colors.deepPurple,
            ),
          ),
          const SizedBox(height: 8),
          ...items.map((item) => Padding(
                padding: const EdgeInsets.only(bottom: 4.0),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text('• ', style: TextStyle(fontSize: 16)),
                    Expanded(
                      child: Text(
                        item,
                        style: const TextStyle(fontSize: 14, height: 1.5),
                      ),
                    ),
                  ],
                ),
              )),
        ],
      ),
    );
  }

  // 切换到指定对话并关闭菜单
  void _switchToChatAndCloseMenu(String sessionId) {
    // 先关闭菜单
    Navigator.pop(context);
    // 然后切换到指定对话
    _switchToChat(sessionId);
  }

  // 重置初始化状态
  void _resetInitializationState() {
    setState(() {
      _isInitializing = false;
      _isCreatingChat = false;
      _hasBeenInitialized = false;
      _currentSessionId = null;
      _messages.clear();
    });
    print('初始化状态已重置');
  }

  @override
  void dispose() {
    _disconnectSSE();
    _scrollController.dispose();
    _textController.dispose();
    _textFieldFocusNode.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text('AIDoc智能文档处理平台', style: TextStyle(color: Colors.grey[700])),
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
        foregroundColor: Theme.of(context).colorScheme.onPrimary,
        actions: [
          IconButton(
            icon: Icon(Icons.refresh, color: Colors.grey[700]),
            onPressed: _refreshAllSections,
            tooltip: '刷新文件区',
          ),
          IconButton(
            icon: Icon(Icons.delete_sweep, color: Colors.grey[700]),
            onPressed: _clearFileCache,
            tooltip: '清除文件缓存',
          ),
          IconButton(
            icon: Icon(Icons.add, color: Colors.grey[700]),
            onPressed: _createNewChat,
            tooltip: '新建对话',
          ),
          IconButton(
            icon: _isLoadingMenu
                ? const SizedBox(
                    width: 24,
                    height: 24,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : Icon(Icons.history, color: Colors.grey[700]),
            tooltip: '对话历史',
            onPressed: () async {
              if (_isLoadingMenu) return;
              setState(() => _isLoadingMenu = true);

              // 加载最新会话列表
              await _loadChatSessions();

              if (!mounted) {
                setState(() => _isLoadingMenu = false);
                return;
              }

              setState(() => _isLoadingMenu = false);

              final RenderBox button = context.findRenderObject() as RenderBox;
              final RenderBox overlay =
                  Overlay.of(context).context.findRenderObject() as RenderBox;
              final Offset buttonTopLeft =
                  button.localToGlobal(Offset.zero, ancestor: overlay);
              final Offset buttonBottomRight = button.localToGlobal(
                  button.size.bottomRight(Offset.zero),
                  ancestor: overlay);
              final Rect buttonRect =
                  Rect.fromPoints(buttonTopLeft, buttonBottomRight);
              final Rect menuRect = buttonRect.shift(const Offset(80, 40));
              final RelativeRect position =
                  RelativeRect.fromRect(menuRect, Offset.zero & overlay.size);

              // 弹出菜单并等待用户选择
              final String? result = await showMenu<String>(
                context: context,
                position: position,
                elevation: 8.0,
                constraints: const BoxConstraints(
                  minWidth: 150,
                  maxWidth: 175,
                  maxHeight: 250,
                ),
                shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(4.0)),
                items: _buildMenuItems(),
              );

              if (result != null) {
                _switchToChat(result);
              }
            },
          ),
          IconButton(
            icon: Icon(Icons.settings, color: Colors.grey[700]),
            onPressed: _showConfigDialog,
            tooltip: '配置',
          ),
          IconButton(
            icon: Icon(Icons.info_outline, color: Colors.grey[700]),
            onPressed: _showHelpDialog,
            tooltip: '帮助文档',
          ),
          IconButton(
            icon: Icon(Icons.logout, color: Colors.grey[700]),
            onPressed: _logout,
            tooltip: '登出',
          ),
        ],
      ),
      body: Container(
        constraints: const BoxConstraints(minWidth: 1024, minHeight: 768),
        child: Row(
          children: [
            // 左侧四个区域 - 分为上下两排，每排两个区域
            Expanded(flex: 1, child: _buildLeftPanel()),
            // 右侧聊天区域
            Expanded(flex: 1, child: _buildChatPanel()),
          ],
        ),
      ),
    );
  }

  Widget _buildLeftPanel() {
    return Column(
      children: [
        // 上排两个区域
        Expanded(
          flex: 1,
          child: Row(
            children: [
              Expanded(
                child: FileSection(
                  key: _waitingSectionKey,
                  title: '等待',
                  onFilesChanged: _refreshAllSections,
                ),
              ),
              Expanded(
                child: FileSection(
                  key: _readSectionKey,
                  title: '读取',
                  onFilesChanged: _refreshAllSections,
                ),
              ),
            ],
          ),
        ),
        // 下排两个区域
        Expanded(
          flex: 1,
          child: Row(
            children: [
              Expanded(
                child: FileSection(
                  key: _templateSectionKey,
                  title: '模板',
                  onFilesChanged: _refreshAllSections,
                ),
              ),
              Expanded(
                child: FileSection(
                  key: _resultSectionKey,
                  title: '结果',
                  onFilesChanged: _refreshAllSections,
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildChatPanel() {
    // 如果正在初始化，显示加载指示器
    if (_isInitializing) {
      return const Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            CircularProgressIndicator(),
            SizedBox(height: 16),
            Text(
              '正在初始化对话...',
              style: TextStyle(fontSize: 16, fontWeight: FontWeight.w500),
            ),
          ],
        ),
      );
    }

    return Column(
      children: [
        Expanded(
          child: Builder(builder: (context) {
            try {
              return ListView.builder(
                controller: _scrollController,
                itemCount: _messages.length + (_isLoading ? 1 : 0),
                itemBuilder: (context, index) {
                  if (index >= _messages.length) {
                    return _buildLoadingIndicator();
                  }
                  return ChatMessageItem(message: _messages[index]);
                },
              );
            } catch (e) {
              return const Center(child: Text("聊天区域加载出错"));
            }
          }),
        ),
        _buildInputArea(),
      ],
    );
  }

  Widget _buildLoadingIndicator() {
    return Padding(
      padding: const EdgeInsets.all(8.0),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Container(
            padding: const EdgeInsets.all(12.0),
            decoration: BoxDecoration(
              color: Colors.blue[50],
              borderRadius: BorderRadius.circular(8.0),
              border: Border.all(
                color: const Color.fromARGB(255, 177, 197, 213),
                width: 2.0,
              ),
            ),
            child: Row(
              children: [
                const CircularProgressIndicator(),
                const SizedBox(width: 10),
                Text(
                  _toolStatusText ?? 'AI正在思考...',
                  style: const TextStyle(
                      fontWeight: FontWeight.bold, fontSize: 16.0),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildInputArea() {
    return Padding(
      padding: const EdgeInsets.all(8.0),
      child: Row(
        children: [
          Expanded(
            child: TextField(
              focusNode: _textFieldFocusNode,
              controller: _textController,
              decoration: const InputDecoration(
                hintText: '请输入您的问题...',
                border: OutlineInputBorder(),
              ),
              onSubmitted: _sendMessage,
            ),
          ),
          IconButton(
            onPressed: () => _sendMessage(_textController.text),
            icon: Icon(Icons.send, color: Colors.grey[700]),
          ),
        ],
      ),
    );
  }

  // 提取菜单项构建逻辑为独立方法
  List<PopupMenuItem<String>> _buildMenuItems() {
    if (_chatSessions.isEmpty) {
      return const [
        PopupMenuItem<String>(
          enabled: false,
          child: Text('暂无对话历史'),
        ),
      ];
    }
    return List.generate(_chatSessions.length, (index) {
      var session = _chatSessions[index];
      String sessionId = session['id'];
      String title = '对话 ${index + 1}';
      return _buildChatSessionItem(
        sessionId,
        title,
        _currentSessionId == sessionId,
      );
    });
  }

  // 构建单个对话会话项
  PopupMenuItem<String> _buildChatSessionItem(
      String sessionId, String title, bool isSelected) {
    return PopupMenuItem<String>(
      value: sessionId,
      height: 40, // 设置固定高度
      padding: EdgeInsets.zero,
      child: _ChatSessionItemWidget(
        sessionId: sessionId,
        title: title,
        isSelected: isSelected,
        onSelect: () {
          _switchToChatAndCloseMenu(sessionId);
        },
        onDelete: () async {
          // 防止重复删除
          if (_isDeletingChat) return;

          setState(() {
            _isDeletingChat = true;
          });

          // 删除对话
          try {
            final response = await ApiService.deleteChatSession(sessionId);
            if (response['code'] == 200) {
              // 关闭下拉菜单
              Navigator.pop(context);

              // 断开该会话的 SSE 连接
              await _disconnectSSE(sessionId);

              // 如果删除的是当前对话，则使用统一处理逻辑
              if (sessionId == _currentSessionId) {
                await _handleDeleteCurrentChat();
              } else {
                // 非当前对话被删除，只需刷新提示
                if (mounted) {
                  TooltipUtil.showTooltip(
                      '对话已删除', TooltipPosition.windowCenter);
                }
                // 刷新对话列表
                await _loadChatSessions();
              }
            } else {
              _showErrorTooltip(
                  '删除对话失败: ${response['msg'] ?? response['message']}');
            }
          } catch (e) {
            _showErrorTooltip('删除对话时发生错误: $e');
          } finally {
            if (mounted) {
              setState(() {
                _isDeletingChat = false;
              });
            }
          }
        },
      ),
    );
  }

  // 显示所有对话的对话框
  void _showAllChatSessionsDialog() {
    showDialog(
      context: context,
      builder: (BuildContext context) {
        return AlertDialog(
          title: const Text('所有对话历史'),
          content: SizedBox(
            width: 400,
            height: 400,
            child: ListView.builder(
              itemCount: _chatSessions.length,
              itemBuilder: (context, index) {
                var session = _chatSessions[index];
                String sessionId = session['id'];
                String title = '对话 ${index + 1}';
                bool isSelected = _currentSessionId == sessionId;

                return ListTile(
                  title: Text(title),
                  selected: isSelected,
                  onTap: () {
                    _switchToChatAndCloseMenu(sessionId);
                  },
                  trailing: IconButton(
                    icon: Icon(Icons.close, size: 18, color: Colors.red),
                    onPressed: () async {
                      // 防止重复删除
                      if (_isDeletingChat) return;

                      setState(() {
                        _isDeletingChat = true;
                      });

                      try {
                        final response =
                            await ApiService.deleteChatSession(sessionId);
                        if (response['code'] == 200) {
                          // 关闭对话框
                          Navigator.pop(context);

                          // 断开该会话的 SSE 连接
                          await _disconnectSSE(sessionId);

                          // 如果删除的是当前对话，则使用统一处理逻辑
                          if (sessionId == _currentSessionId) {
                            await _handleDeleteCurrentChat();
                          } else {
                            // 非当前对话被删除，只需刷新提示
                            if (mounted) {
                              TooltipUtil.showTooltip(
                                  '对话已删除', TooltipPosition.windowCenter);
                            }
                            // 刷新对话列表
                            await _loadChatSessions();
                          }
                        } else {
                          _showErrorTooltip(
                              '删除对话失败: ${response['msg'] ?? response['message']}');
                        }
                      } catch (e) {
                        _showErrorTooltip('删除对话时发生错误: $e');
                      } finally {
                        if (mounted) {
                          setState(() {
                            _isDeletingChat = false;
                          });
                        }
                      }
                    },
                  ),
                );
              },
            ),
          ),
          actions: [
            TextButton(
              onPressed: () {
                Navigator.of(context).pop();
              },
              child: const Text('关闭'),
            ),
          ],
        );
      },
    );
  }

  // 刷新结果区文件显示
  Future<void> _refreshResultSection() async {
    try {
      // 使用 GlobalKey 调用 FileSection 的 refreshFiles方法
      final resultState = _resultSectionKey.currentState;
      if (resultState != null && mounted) {
        resultState.refreshFiles();
        _logger.d('结果区文件已刷新');
      }
    } catch (e) {
      _logger.e('刷新结果区失败：$e');
    }
  }

  // 初始化：只检查最新对话是否为空，否则创建新对话
  Future<void> _smartInitializeChat() async {
    try {
      // 加载对话列表
      await _loadChatSessions();

      if (_chatSessions.isEmpty) {
        // 没有任何对话，直接创建新对话
        await _createNewChat();
        return;
      }

      // 取最后一个会话作为"最新的对话"
      final latestSession = _chatSessions.last;
      final latestSessionId = latestSession['id'];

      // 获取该会话的历史记录，判断是否为空
      final historyResponse =
          await ApiService.getChatHistoryBySession(latestSessionId);
      bool isEmptyChat = false;
      if (historyResponse['code'] == 200) {
        List<dynamic> chatHistory = historyResponse['data'] ?? [];
        isEmptyChat = chatHistory.isEmpty ||
            (chatHistory.length == 1 && chatHistory[0]['senderType'] != 'USER');
      }

      if (isEmptyChat) {
        // 复用最新空对话
        if (mounted) {
          // 显示提示（在状态更新后立即显示）
          TooltipUtil.showTooltip('已复用最新空对话', TooltipPosition.windowCenter);

          setState(() {
            _currentSessionId = latestSessionId;
            _messages.clear();
            _currentDocumentContent = '';
          });

          await _disconnectSSE();
          // 加载历史消息
          await _loadChatHistoryForCurrentSession();
          // 连接新会话的 SSE
          await _connectSSE(latestSessionId);
          // 保存到本地存储
          final prefs = await SharedPreferences.getInstance();
          await prefs.setString('last_session_id', latestSessionId);
        }
      } else {
        // 最新对话不为空，创建新对话
        await _createNewChat();
      }
    } catch (e) {
      _logger.e('智能初始化对话时发生错误: $e');
      // 出错时回退到创建新对话
      await _createNewChat();
    }
  }

  // 检查并创建新对话
  Future<void> _initializeNewChat() async {
    try {
      // 使用统一的刷新方法加载对话列表
      await _loadChatSessions();

      // 直接创建新对话
      await _createNewChat();
    } catch (e) {
      _logger.e('初始化新对话时发生错误：$e');
      // 如果创建新对话失败，尝试切换到现有对话
      if (_chatSessions.isNotEmpty) {
        _switchToChat(_chatSessions.first['id']);
      }
    }
  }

  // 检查并复用空对话（保留该方法但不再使用）
  Future<void> _checkAndReuseEmptyChat() async {
    try {
      // 使用统一的刷新方法
      await _loadChatSessions();

      // 1. 尝试恢复上次使用的会话
      final prefs = await SharedPreferences.getInstance();
      final lastSessionId = prefs.getString('last_session_id');
      if (lastSessionId != null &&
          _chatSessions.any((s) => s['id'] == lastSessionId)) {
        _switchToChat(lastSessionId);
        return;
      }

      // 2. 如果没有上次会话或已失效，尝试复用空对话
      bool success = false;
      for (var session in _chatSessions) {
        String sessionId = session['id'];
        final historyResponse =
            await ApiService.getChatHistoryBySession(sessionId);
        if (historyResponse['code'] == 200) {
          List<dynamic> chatHistory = historyResponse['data'] ?? [];
          bool isEmptyChat = chatHistory.isEmpty ||
              (chatHistory.length == 1 &&
                  chatHistory[0]['senderType'] != 'USER');

          if (isEmptyChat) {
            if (mounted) {
              setState(() {
                _currentSessionId = sessionId;
                _messages.clear();
                _currentDocumentContent = '';
              });
              await _loadChatHistoryForCurrentSession();
              WidgetsBinding.instance.addPostFrameCallback((_) {
                if (mounted) {
                  TooltipUtil.showTooltip(
                      '已复用空对话', TooltipPosition.windowCenter);
                }
              });
            }
            success = true;
            break;
          }
        }
      }

      // 3. 仍然失败，则选择第一个会话或新建
      if (!success) {
        if (_chatSessions.isNotEmpty) {
          _switchToChat(_chatSessions.first['id']);
        } else {
          _createNewChat();
        }
      }
    } catch (e) {
      _logger.e('检查空对话时发生严重错误: $e');
    }
  }
}

// 对话会话项组件（纯Widget）
class _ChatSessionItemWidget extends StatefulWidget {
  final String sessionId;
  final String title;
  final bool isSelected;
  final VoidCallback onSelect;
  final VoidCallback onDelete;

  const _ChatSessionItemWidget({
    required this.sessionId,
    required this.title,
    required this.isSelected,
    required this.onSelect,
    required this.onDelete,
  });

  @override
  State<_ChatSessionItemWidget> createState() => _ChatSessionItemWidgetState();
}

class _ChatSessionItemWidgetState extends State<_ChatSessionItemWidget> {
  bool _isHovered = false;

  @override
  Widget build(BuildContext context) {
    return MouseRegion(
      onEnter: (_) => setState(() => _isHovered = true),
      onExit: (_) => setState(() => _isHovered = false),
      child: Container(
        margin: const EdgeInsets.symmetric(horizontal: 0),
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 100), // 添加平滑过渡
          decoration: BoxDecoration(
            color: widget.isSelected
                ? Theme.of(context)
                    .primaryColor
                    .withValues(alpha: 0.15) // 选中颜色更深
                : _isHovered
                    ? Colors.grey.withValues(alpha: 0.1) // 悬停颜色更浅
                    : Colors.transparent,
            borderRadius: BorderRadius.circular(4.0),
          ),
          padding: const EdgeInsets.only(top: 4.0, bottom: 4.0),
          child: GestureDetector(
            onTap: widget.onSelect,
            child: Row(
              mainAxisSize: MainAxisSize.max, // 确保Row占满可用空间
              children: [
                Expanded(
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 16.0),
                    child: Text(
                      widget.title,
                      overflow: TextOverflow.ellipsis,
                      style: widget.isSelected
                          ? TextStyle(fontWeight: FontWeight.bold)
                          : null,
                    ),
                  ),
                ),
                // 使用固定大小的容器
                SizedBox(
                  width: 40, // 固定宽度
                  child: Center(
                    child: _isHovered
                        ? IconButton(
                            icon: Icon(Icons.close,
                                size: 18, color: Colors.grey[700]),
                            onPressed: widget.onDelete,
                            padding: EdgeInsets.zero,
                            constraints: const BoxConstraints(
                                minWidth: 24, minHeight: 24), // 最小约束
                          )
                        : const SizedBox(
                            width: 24, height: 24), // 非悬停时显示一个固定大小的占位
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

import 'package:flutter/material.dart';
import 'dart:async';
import 'package:logger/logger.dart'; // 导入logger包
import 'services/api_service.dart';
import 'models/chat_message.dart';
import 'widgets/tips.dart';
import 'login_page.dart';
import 'widgets/file_section.dart';
import 'widgets/chat_message_item.dart';

void main() async {
  // 加载配置
  WidgetsFlutterBinding.ensureInitialized();

  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'AI智能文档助手',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
        useMaterial3: true,
      ),
      home: const AuthWrapper(), // 使用认证包装器
    );
  }
}

// 认证包装器组件，检查登录状态
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
      // 检查是否存在有效的认证令牌
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
    return const MyHomePage();
  }
}

class MyHomePage extends StatefulWidget {
  const MyHomePage({super.key});

  @override
  State<MyHomePage> createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  final List<ChatMessage> _messages = [];
  final TextEditingController _textController = TextEditingController();
  final ScrollController _scrollController = ScrollController();
  final FocusNode _textFieldFocusNode = FocusNode();
  bool _isLoading = false;

  // 创建logger实例
  final Logger _logger = Logger();

  // 为每个区域创建 GlobalKey
  final GlobalKey<FileSectionState> _waitingSectionKey = GlobalKey();
  final GlobalKey<FileSectionState> _readSectionKey = GlobalKey();
  final GlobalKey<FileSectionState> _templateSectionKey = GlobalKey();
  final GlobalKey<FileSectionState> _resultSectionKey = GlobalKey();

  // 存储已上传文档的内容
  String _currentDocumentContent = '';

  // 当前对话ID
  String? _currentSessionId;

  // 对话历史列表
  List<Map<String, dynamic>> _chatSessions = [];

  // 添加一个状态标记，用于控制对话历史菜单的重建
  int _chatSessionListVersion = 0;

  @override
  void initState() {
    super.initState();
    // 初始化完成后自动开始新对话
    _createNewChat();
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

  // 创建新对话
  void _createNewChat() async {
    try {
      final response = await ApiService.createNewChat();
      if (response['code'] == 200 && response['data'] != null) {
        String newSessionId = response['data']['sessionId'];

        if (mounted) {
          setState(() {
            _currentSessionId = newSessionId;
            _messages.clear();
            _currentDocumentContent = '';
          });

          // 显示新对话开始的提示消息
          WidgetsBinding.instance.addPostFrameCallback((_) {
            if (mounted) {
              setState(() {
                _messages.add(
                  ChatMessage.ai('您好！我是您的AI智能文档助手，有什么我可以帮您的吗？'),
                );
              });
            }
          });

          // 刷新对话历史
          _loadChatSessions();
        }
      } else {
        _showErrorTooltip('创建新对话失败: ${response['msg'] ?? response['message']}');
      }
    } catch (e) {
      _showErrorTooltip('创建新对话时发生错误: $e');
    }
  }

  // 加载对话历史
  void _loadChatSessions() async {
    try {
      final response = await ApiService.getChatSessions();
      if (response['code'] == 200) {
        if (mounted) {
          setState(() {
            _chatSessions =
                List<Map<String, dynamic>>.from(response['data'] ?? []);
            // 更新版本号，强制重建对话历史菜单
            _chatSessionListVersion++;
          });
        }
      } else {
        _showErrorTooltip(
            '获取对话历史失败: ${response['msg'] ?? response['message']}');
      }
    } catch (e) {
      _showErrorTooltip('获取对话历史时发生错误: $e');
    }
  }

  // 切换到指定对话
  void _switchToChat(String sessionId) async {
    try {
      final response = await ApiService.getChatHistoryBySession(sessionId);
      if (response['code'] == 200) {
        List<dynamic> chatMessages = response['data'] ?? [];

        if (mounted) {
          setState(() {
            _currentSessionId = sessionId;
            _messages.clear();

            // 添加默认提示信息到对话开头
            _messages.add(
              ChatMessage.ai('您好！我是您的AI智能文档助手，有什么我可以帮您的吗？'),
            );

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

          // 显示切换对话成功的提示
          if (mounted) {
            TooltipUtil.showTooltip('已切换到对话', TooltipPosition.windowCenter);
          }
        }
      } else {
        _showErrorTooltip(
            '获取对话历史失败: ${response['msg'] ?? response['message']}');
      }
    } catch (e) {
      _showErrorTooltip('获取对话历史时发生错误: $e');
    }
  }

  // 删除当前对话
  void _deleteCurrentChat() async {
    if (_currentSessionId == null) {
      _showErrorTooltip('没有可删除的对话');
      return;
    }

    try {
      final response = await ApiService.deleteChatSession(_currentSessionId!);
      if (response['code'] == 200) {
        // 删除成功后，创建一个新的对话
        _createNewChat();
      } else {
        _showErrorTooltip('删除对话失败: ${response['message']}');
      }
    } catch (e) {
      _showErrorTooltip('删除对话时发生错误: $e');
    }
  }

  // 显示错误提示（使用tips组件）
  void _showErrorTooltip(String message) {
    if (mounted) {
      TooltipUtil.showTooltip(message, TooltipPosition.windowCenter);
    }
  }

  // 发送消息
  Future<void> _sendMessage(String text) async {
    if (text.isEmpty || _currentSessionId == null) return;

    // 滚动到最新消息
    _scrollToBottom();

    try {
      setState(() {
        _messages.add(ChatMessage.user(text));
        _textController.clear();
        _isLoading = true;
      });

      // 直接调用后端API，让后端处理工具决策和AI响应
      final response = await _callBackendAPI(text, _currentDocumentContent);

      if (response != null) {
        setState(() {
          _messages.add(ChatMessage.ai(response));
        });
      }
    } catch (e) {
      // 添加错误消息到聊天界面
      if (mounted) {
        setState(() {
          _messages.add(ChatMessage.ai('处理您的请求时发生了错误，请稍后重试。'));
        });
      }
    } finally {
      if (mounted) {
        setState(() {
          _isLoading = false;
        });
      }
      // AI回复后再次滚动到底部
      _scrollToBottom();

      // 发送消息后焦点回到输入框
      if (mounted) {
        FocusScope.of(context).requestFocus(_textFieldFocusNode);
      }
    }
  }

  // 调用后端API
  Future<String?> _callBackendAPI(String question, String documentText) async {
    try {
      // 使用实际存在的API端点，并传递sessionId
      final response =
          await ApiService.sendChatMessage(question, _currentSessionId);

      if (response['code'] == 200) {
        // 根据后端实际返回格式，data字段直接是AI响应字符串
        final aiResponse = response['data'] != null
            ? response['data']
            : (response['msg'] ?? response['message']);

        return aiResponse ?? '未收到后端响应';
      } else {
        _logger.e('API调用失败或返回null');
        return '后端服务暂时不可用，请稍后重试: ${response['msg'] ?? response['message']}';
      }
    } catch (e) {
      _logger.e('API调用异常: $e');
      return '连接后端服务时发生错误';
    }
  }

  void _refreshAllSections() {
    // 使用Future.microtask确保在下次事件循环时执行刷新
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        // 使用Future.microtask确保在下次事件循环中执行
        Future.microtask(() {
          if (mounted) {
            // 调用每个区域的刷新方法
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
              // 检查组件是否仍然挂载后再执行刷新
              if (mounted) {
                state!.refreshFiles();
              }
            });
          }
        });
      }
    });
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
                  'siliconFlowApiKey': controllers['siliconFlowApiKey']!.text,
                  'siliconFlowBaseUrl': controllers['siliconFlowBaseUrl']!.text,
                  'chatModelName': controllers['chatModelName']!.text,
                  'decisionModelName': controllers['decisionModelName']!.text,
                  'analysisModelName': controllers['analysisModelName']!.text,
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
                        '配置更新失败: ${updateResponse['message']}',
                        TooltipPosition.windowCenter);
                  }
                }
              },
              child: const Text('保存'),
            ),
          ],
        );
      },
    );
  }

  // 切换到指定对话并关闭菜单
  void _switchToChatAndCloseMenu(String sessionId) {
    // 先关闭菜单
    Navigator.pop(context);
    // 然后切换到指定对话
    _switchToChat(sessionId);
  }

  @override
  void dispose() {
    _scrollController.dispose();
    _textController.dispose();
    _textFieldFocusNode.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text('AI智能文档助手', style: TextStyle(color: Colors.grey[700])),
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
        foregroundColor: Theme.of(context).colorScheme.onPrimary,
        actions: [
          IconButton(
            icon: Icon(Icons.refresh, color: Colors.grey[700]),
            onPressed: _refreshAllSections,
            tooltip: '刷新工作区',
          ),
          IconButton(
            icon: Icon(Icons.add, color: Colors.grey[700]),
            onPressed: _createNewChat,
            tooltip: '新建对话',
          ),
          PopupMenuButton(
            icon: Icon(Icons.history, color: Colors.grey[700]),
            tooltip: '对话历史',
            onSelected: (value) {},
            itemBuilder: (context) {
              // 在显示菜单之前先刷新列表
              return List.generate(_chatSessions.length, (index) {
                var session = _chatSessions[index];
                String sessionId = session['id'];
                // 按顺序显示对话标题（对话1、对话2等）
                String title = '对话 ${index + 1}';

                return _buildChatSessionItem(
                    sessionId, title, _currentSessionId == sessionId);
              })
                ..addAll([
                  if (_chatSessions.isEmpty)
                    const PopupMenuItem(
                      enabled: false,
                      child: Text('暂无对话历史'),
                    ),
                ]);
            },
            onOpened: () {
              // 当下拉菜单打开时刷新列表
              _loadChatSessions();
            },
            // 添加key以强制重建菜单
            key: ValueKey(_chatSessionListVersion),
            elevation: 8.0,
            // 添加偏移量，使下拉菜单向下偏移，避免遮挡顶栏
            offset: const Offset(80, 40), // 向下偏移40像素
            // 设置下拉菜单的约束，修改最大宽度为175
            constraints: const BoxConstraints(
              minWidth: 150, // 保持最小宽度
              maxWidth: 175, // 修改为175的最大宽度
              maxHeight: 250, // 设置最大高度以启用滚动
            ),
            surfaceTintColor: Colors.transparent,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(4.0),
            ),
          ),
          IconButton(
            icon: Icon(Icons.settings, color: Colors.grey[700]),
            onPressed: _showConfigDialog,
            tooltip: '配置',
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
          // 删除对话
          try {
            final response = await ApiService.deleteChatSession(sessionId);
            if (response['code'] == 200) {
              // 关闭下拉菜单
              Navigator.pop(context);

              // 显示删除成功提示
              if (mounted) {
                TooltipUtil.showTooltip('对话已删除', TooltipPosition.windowCenter);
              }

              // 刷新对话列表
              _loadChatSessions();

              // 如果删除的是当前对话，则创建新对话
              if (sessionId == _currentSessionId) {
                _createNewChat();
              }
            } else {
              _showErrorTooltip(
                  '删除对话失败: ${response['msg'] ?? response['message']}');
            }
          } catch (e) {
            _showErrorTooltip('删除对话时发生错误: $e');
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
                      try {
                        final response =
                            await ApiService.deleteChatSession(sessionId);
                        if (response['code'] == 200) {
                          // 关闭对话框
                          Navigator.pop(context);

                          // 显示删除成功提示
                          if (mounted) {
                            TooltipUtil.showTooltip(
                                '对话已删除', TooltipPosition.windowCenter);
                          }

                          // 刷新对话列表
                          _loadChatSessions();

                          // 如果删除的是当前对话，则创建新对话
                          if (sessionId == _currentSessionId) {
                            _createNewChat();
                          }
                        } else {
                          _showErrorTooltip(
                              '删除对话失败: ${response['msg'] ?? response['message']}');
                        }
                      } catch (e) {
                        _showErrorTooltip('删除对话时发生错误: $e');
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

  Widget _buildChatPanel() {
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
            child: const Row(
              children: [
                CircularProgressIndicator(),
                SizedBox(width: 5),
                Text(
                  'AI正在思考...',
                  style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16.0),
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
}

// 对话会话项组件（纯Widget，不包含PopupMenuItem）
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
                ? Theme.of(context).primaryColor.withValues(alpha: 0.15) // 选中颜色更深
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
                            icon: Icon(Icons.close, size: 18, color: Colors.grey[700]),
                            onPressed: widget.onDelete,
                            padding: EdgeInsets.zero, // 移除内边距
                            constraints: const BoxConstraints(minWidth: 24, minHeight: 24), // 最小约束
                          )
                        : const SizedBox(width: 24, height: 24), // 非悬停时显示一个固定大小的占位
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

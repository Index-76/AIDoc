import 'package:flutter/material.dart';
import 'dart:async';
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

  // 为每个区域创建 GlobalKey
  final GlobalKey<FileSectionState> _waitingSectionKey = GlobalKey();
  final GlobalKey<FileSectionState> _readSectionKey = GlobalKey();
  final GlobalKey<FileSectionState> _templateSectionKey = GlobalKey();
  final GlobalKey<FileSectionState> _resultSectionKey = GlobalKey();

  // 存储已上传文档的内容
  String _currentDocumentContent = '';

  @override
  void initState() {
    super.initState();
    // 初始化完成后自动开始新对话
    _resetChat();
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

  void _resetChat() {
    setState(() {
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
  }

  // 发送消息
  Future<void> _sendMessage(String text) async {
    if (text.isEmpty) return;

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
      // 使用新的带工具决策的API端点
      final response = await ApiService.chatWithToolDecision(question);

      if (response != null) {
        // 检查是否使用了工具
        final toolUsed = response['toolUsed'] ?? false;
        final toolInfo = response['toolInfo'];
        final aiResponse = response['response'];

        if (toolUsed && toolInfo != null) {
          print('工具已使用: ${toolInfo['specificToolName']}');
        } else {
          print('未使用工具，直接AI回复');
        }

        return aiResponse ?? '未收到后端响应';
      } else {
        print('API调用失败或返回null');
        return '后端服务暂时不可用，请稍后重试';
      }
    } catch (e) {
      print('API调用异常: $e');
      return '连接后端服务时发生错误';
    }
  }

  void _refreshAllSections() {
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
        .forEach((state) => state!.refreshFiles());
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
      print('登出时发生错误: $e');
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

  @override
  void dispose() {
    _scrollController.dispose();
    _textController.dispose();
    _textFieldFocusNode.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return TooltipOverlay(
      // 使用TooltipOverlay包装整个界面
      child: Scaffold(
        appBar: AppBar(
          backgroundColor: Theme.of(context).colorScheme.inversePrimary,
          title: const Text('AI智能文档助手'),
          actions: [
            IconButton(
              icon: const Icon(Icons.refresh),
              onPressed: _refreshAllSections,
              tooltip: '刷新',
            ),
            IconButton(
              icon: const Icon(Icons.add),
              onPressed: _resetChat,
              tooltip: '新对话',
            ),
            IconButton(
              icon: const Icon(Icons.logout),
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
    return Column(
      children: [
        Expanded(
          child: ListView.builder(
            controller: _scrollController,
            itemCount: _messages.length + (_isLoading ? 1 : 0),
            itemBuilder: (context, index) {
              if (index >= _messages.length) {
                return _buildLoadingIndicator();
              }
              return ChatMessageItem(message: _messages[index]);
            },
          ),
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
            icon: const Icon(Icons.send),
          ),
        ],
      ),
    );
  }
}

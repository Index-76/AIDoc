class ChatMessage {
  final String text;
  final bool isUser;
  final bool isToolCall; // 是否是工具调用提示消息

  ChatMessage({required this.text, required this.isUser, this.isToolCall = false});

  // 添加工厂构造函数
  factory ChatMessage.user(String text) {
    return ChatMessage(text: text, isUser: true);
  }

  factory ChatMessage.ai(String text) {
    return ChatMessage(text: text, isUser: false);
  }
}
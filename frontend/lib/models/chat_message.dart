enum ChatSender {
  user,
  ai,
  system, // 添加系统消息类型
}

class ChatMessage {
  final String text;
  final ChatSender sender;
  final bool isToolCall;

  ChatMessage(this.text, this.sender, {this.isToolCall = false});

  factory ChatMessage.user(String text) {
    return ChatMessage(text, ChatSender.user);
  }

  factory ChatMessage.ai(String text) {
    return ChatMessage(text, ChatSender.ai);
  }

  factory ChatMessage.system(String text) {
    return ChatMessage(text, ChatSender.system, isToolCall: true);
  }

  bool get isUser => sender == ChatSender.user;
  bool get isSystem => sender == ChatSender.system; // 添加系统消息判断
}
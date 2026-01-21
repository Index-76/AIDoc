enum ChatSender {
  user,
  ai,
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

  bool get isUser => sender == ChatSender.user;
}
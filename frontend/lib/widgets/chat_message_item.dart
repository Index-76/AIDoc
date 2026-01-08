import 'package:flutter/material.dart';
import 'package:flutter_markdown/flutter_markdown.dart';
import 'package:markdown/markdown.dart' as md;
import '../models/chat_message.dart';
import 'copy_button.dart';

class ChatMessageItem extends StatelessWidget {
  final ChatMessage message;

  const ChatMessageItem({super.key, required this.message});

  @override
  Widget build(BuildContext context) {
    if (message.isToolCall) {
      return _buildToolCallMessage(context);
    } else if (message.isUser) {
      return _buildUserMessage(context);
    } else {
      return _buildAIMessage(context);
    }
  }

  Widget _buildToolCallMessage(BuildContext context) {
    return Container(
      margin: const EdgeInsets.symmetric(vertical: 4.0, horizontal: 8.0),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Container(
            padding: EdgeInsets.symmetric(
              horizontal: MediaQuery.of(context).size.width > 768 ? 12.0 : 8.0,
              vertical: MediaQuery.of(context).size.width > 768 ? 8.0 : 6.0,
            ),
            decoration: BoxDecoration(
              color: Colors.orange[100],
              borderRadius: BorderRadius.circular(12.0),
              border: Border.all(
                color: Colors.orangeAccent,
                width: 1.0,
              ),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(
                  Icons.info_outline,
                  size: 16.0,
                  color: Colors.orange,
                ),
                const SizedBox(width: 4.0),
                Text(
                  message.text,
                  style: TextStyle(
                    fontSize: MediaQuery.of(context).size.width > 768 ? 14.0 : 12.0,
                    color: Colors.orange,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildUserMessage(BuildContext context) {
    return Container(
      margin: const EdgeInsets.symmetric(vertical: 4.0, horizontal: 8.0),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.end,
        children: [
          Flexible(
            child: Container(
              padding: EdgeInsets.all(MediaQuery.of(context).size.width > 768 ? 12.0 : 10.0),
              decoration: BoxDecoration(
                color: Colors.blue[100],
                borderRadius: BorderRadius.circular(8.0),
              ),
              child: SelectionArea(
                child: Text(
                  message.text,
                  style: TextStyle(
                    fontSize: MediaQuery.of(context).size.width > 768 ? 16.0 : 14.0,
                    fontWeight: FontWeight.normal,
                    letterSpacing: 0.5,
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildAIMessage(BuildContext context) {
    return Container(
      margin: const EdgeInsets.symmetric(vertical: 4.0, horizontal: 8.0),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.start,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Flexible(
            child: _BubbleWithCopyButton(message: message),
          ),
        ],
      ),
    );
  }
}

class _BubbleWithCopyButton extends StatefulWidget {
  final ChatMessage message;

  const _BubbleWithCopyButton({required this.message});

  @override
  State<_BubbleWithCopyButton> createState() => _BubbleWithCopyButtonState();
}

class _BubbleWithCopyButtonState extends State<_BubbleWithCopyButton> {
  bool _isHovering = false;
  bool _isButtonHovered = false;

  @override
  Widget build(BuildContext context) {
    return Stack(
      children: [
        MouseRegion(
          onEnter: (_) => setState(() => _isHovering = true),
          onExit: (_) => setState(() => _isHovering = false),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Flexible(
                child: Container(
                  padding: EdgeInsets.all(MediaQuery.of(context).size.width > 768 ? 12.0 : 10.0),
                  decoration: BoxDecoration(
                    color: Colors.grey[300],
                    borderRadius: BorderRadius.circular(8.0),
                  ),
                  child: SelectionArea(
                    child: MarkdownBody(
                      data: widget.message.text,
                      styleSheet: MarkdownStyleSheet(
                        p: TextStyle(
                          fontSize: MediaQuery.of(context).size.width > 768 ? 16.0 : 14.0,
                          fontWeight: FontWeight.normal,
                          letterSpacing: 0.5,
                        ),
                        code: TextStyle(
                          fontFamily: 'Courier New',
                          fontSize: MediaQuery.of(context).size.width > 768 ? 14.0 : 12.0,
                          color: Colors.black,
                        ),
                        codeblockPadding: const EdgeInsets.all(8.0),
                        codeblockDecoration: BoxDecoration(
                          color: const Color(0xFFCCCCCC),
                          borderRadius: BorderRadius.circular(4.0),
                        ),
                        a: const TextStyle(
                          color: Colors.blue,
                          decoration: TextDecoration.underline,
                        ),
                      ),
                      builders: {'code': _CodeElementBuilder()},
                    ),
                  ),
                ),
              ),
              Container(
                width: 40,
                height: 60,
                color: Colors.transparent,
              ),
            ],
          ),
        ),
        Positioned(
          right: 5,
          bottom: 5,
          child: MouseRegion(
            onEnter: (_) => setState(() => _isButtonHovered = true),
            onExit: (_) => setState(() => _isButtonHovered = false),
            child: AnimatedOpacity(
              opacity: _isHovering || _isButtonHovered ? 1.0 : 0.0,
              duration: const Duration(milliseconds: 150),
              child: CopyButton(message: widget.message),
            ),
          ),
        ),
      ],
    );
  }
}

class _CodeElementBuilder extends MarkdownElementBuilder {
  @override
  Widget? visitElementAfter(md.Element element, TextStyle? preferredStyle) {
    return Container(
      padding: const EdgeInsets.all(2),
      decoration: const BoxDecoration(),
      child: Text(
        element.textContent,
        style: TextStyle(
          fontFamily: 'Courier New',
          fontSize: 12.0,
          color: Colors.black,
        ),
      ),
    );
  }
}
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../models/chat_message.dart';
import 'tips.dart';

class CopyButton extends StatefulWidget {
  final ChatMessage message;

  const CopyButton({super.key, required this.message});

  @override
  State<CopyButton> createState() => _CopyButtonState();
}

class _CopyButtonState extends State<CopyButton> {
  bool _isHovered = false;
  bool _isCopying = false; // 防止重复点击的标志位

  @override
  Widget build(BuildContext context) {
    // 根据屏幕宽度和文本缩放因子调整按钮尺寸
    double screenWidth = MediaQuery.of(context).size.width;
    double textScaleFactor = MediaQuery.of(context).textScaleFactor;

    // 基础尺寸
    double baseButtonSize = screenWidth > 768 ? 28.0 : 24.0;
    double baseIconSize = screenWidth > 768 ? 16.0 : 14.0;

    // 考虑文本缩放因子进行调整
    double buttonSize = baseButtonSize * (1 + (textScaleFactor - 1) * 0.5);
    double iconSize = baseIconSize * (1 + (textScaleFactor - 1) * 0.5);

    return MouseRegion(
      onEnter: (_) => setState(() => _isHovered = true),
      onExit: (_) => setState(() => _isHovered = false),
      child: GestureDetector(
        onTap: () {
          // 防止重复点击
          if (_isCopying) return;

          setState(() {
            _isCopying = true;
          });

          Clipboard.setData(ClipboardData(text: widget.message.text));
          TooltipUtil.showTooltip("已复制到剪贴板", TooltipPosition.windowCenter);

          // 重置复制状态
          Future.delayed(const Duration(milliseconds: 300), () {
            if (mounted) {
              setState(() {
                _isCopying = false;
              });
            }
          });
        },
        child: Container(
          height: buttonSize,
          width: buttonSize,
          decoration: BoxDecoration(
            color: _isHovered ? Colors.grey[600] : Colors.white,
            shape: BoxShape.circle,
            boxShadow: [
              BoxShadow(
                color: Colors.black.withOpacity(0.2),
                blurRadius: 4,
              ),
            ],
          ),
          child: Icon(
            Icons.copy,
            size: iconSize,
            color: _isHovered ? Colors.white : Colors.black,
          ),
        ),
      ),
    );
  }
}

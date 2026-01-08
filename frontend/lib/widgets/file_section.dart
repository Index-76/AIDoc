import 'package:flutter/material.dart';
import '../models/file_info.dart';
import 'tips.dart'; // 确保导入tips.dart以使用TooltipUtil和TooltipPosition
import '../services/api_service.dart';

class FileSection extends StatefulWidget {
  final String title;
  final VoidCallback? onFilesChanged;

  const FileSection({super.key, required this.title, this.onFilesChanged});

  @override
  State<FileSection> createState() => FileSectionState();
}

// 将State类改为公开的，以便外部可以引用
class FileSectionState extends State<FileSection> {
  late Future<List<FileInfo>> _filesFuture;
  String _currentPath = '';
  // 防止重复操作的标志位
  bool _isProcessing = false;

  @override
  void initState() {
    super.initState();
    _refreshFiles();
  }


  void _refreshFiles() {
    setState(() {
      _filesFuture = ApiService.listFiles(widget.title, _currentPath);
    });
  }

  void _navigateToDirectory(String dirName) {
    // 防止在处理中时导航
    if (_isProcessing) return;

    setState(() {
      _currentPath = '$_currentPath/$dirName';
      _refreshFiles();
    });
  }

  void _navigateBack() {
    // 防止在处理中时导航
    if (_isProcessing) return;

    if (_currentPath.isNotEmpty) {
      setState(() {
        final parts = _currentPath.split('/');
        parts.removeLast();
        _currentPath = parts.join('/');
        if (_currentPath.startsWith('/')) {
          _currentPath = _currentPath.substring(1);
        }
        _refreshFiles();
      });
    }
  }

  // 导出文件功能
  void _exportFiles() async {
    // 防止在处理中时导出
    if (_isProcessing) return;

    // 获取当前区域的文件列表
    final files = await ApiService.listFiles(widget.title, _currentPath);

    if (files.isEmpty) {
      return;
    }

    // 这里可以选择导出单个文件或多选导出
    if (files.length == 1) {
      setState(() {
        _isProcessing = true;
      });

      // 如果只有一个文件或文件夹，直接导出
      _exportSingleFile(files.first);
    } else {
      // 如果有多个文件，让用户选择要导出的文件
      _selectFilesToExport(files);
    }
  }

  // 导出单个文件或文件夹
  void _exportSingleFile(FileInfo file) async {
    final result = await ApiService.exportFile(file.id);
    bool success = result['code'] == 200;
    
    if (success && mounted) {
      TooltipUtil.showTooltip(
        '${file.isDirectory ? '文件夹' : '文件'}导出成功',
        TooltipPosition.fileAreaCenter,
      );
    } else if (mounted) {
      TooltipUtil.showTooltip(
        '${file.isDirectory ? '文件夹' : '文件'}导出失败',
        TooltipPosition.fileAreaCenter,
      );
    }

    if (mounted) {
      setState(() {
        _isProcessing = false;
      });
    }
  }

  // 选择要导出的文件
  void _selectFilesToExport(List<FileInfo> files) {
    // 防止在处理中时导出
    if (_isProcessing) return;

    final selectedFiles = <FileInfo>[];

    if (mounted) {
      showDialog(
        context: context,
        builder: (BuildContext context) {
          return StatefulBuilder(
            builder: (context, setState) {
              return AlertDialog(
                title: const Text('选择要导出的文件'),
                content: SizedBox(
                  width: double.maxFinite,
                  child: ListView.builder(
                    shrinkWrap: true,
                    itemCount: files.length,
                    itemBuilder: (context, index) {
                      final file = files[index];
                      return CheckboxListTile(
                        value: selectedFiles.contains(file),
                        onChanged: (bool? value) {
                          setState(() {
                            if (value == true) {
                              selectedFiles.add(file);
                            } else {
                              selectedFiles.remove(file);
                            }
                          });
                        },
                        title: Text(file.name),
                        secondary: Icon(
                          file.isDirectory ? Icons.folder : Icons.description,
                        ),
                      );
                    },
                  ),
                ),
                actions: [
                  TextButton(
                    onPressed: () => Navigator.of(context).pop(),
                    child: const Text('取消'),
                  ),
                  TextButton(
                    onPressed: () async {
                      if (selectedFiles.isNotEmpty) {
                        Navigator.of(context).pop();

                        // 设置处理状态
                        setState(() {
                          _isProcessing = true;
                        });

                        // 执行导出操作
                        bool allSuccess = true;
                        for (final file in selectedFiles) {
                          final result = await ApiService.exportFile(file.id);
                          bool success = result['code'] == 200;
                          
                          if (!success) {
                            allSuccess = false;
                          }
                        }

                        // 显示导出结果提示
                        if (mounted) {
                          TooltipUtil.showTooltip(
                            allSuccess ? '文件导出成功' : '部分文件导出失败',
                            TooltipPosition.fileAreaCenter,
                          );

                          setState(() {
                            _isProcessing = false;
                          });
                        }
                      }
                    },
                    child: const Text('导出'),
                  ),
                ],
              );
            },
          );
        },
      );
    }
  }

  // 公共方法，允许外部触发刷新
  void refreshFiles() {
    _refreshFiles();
  }

  // 构建标题文本，处理文字过长问题
  Widget _buildTitleText() {
    if (_currentPath.isEmpty) {
      // 没有路径，只显示区域名
      String sectionDisplay = widget.title;
      if (widget.title == '等待') {
        sectionDisplay = '等待区';
      } else if (widget.title == '读取') {
        sectionDisplay = '读取区';
      } else if (widget.title == '模板') {
        sectionDisplay = '模板区';
      } else if (widget.title == '结果') {
        sectionDisplay = '结果区';
      }
      return Text(
        sectionDisplay,
        style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14.0),
        overflow: TextOverflow.ellipsis,
      );
    }

    // 只显示最内层目录名
    final pathParts = _currentPath.split('/');
    final innermostDir = pathParts.last; // 获取最内层目录名

    // 如果超过10个字符，则截取前10个字符并添加省略号
    String displayText = innermostDir;
    if (innermostDir.length > 10) {
      displayText = '${innermostDir.substring(0, 10)}...';
    }

    return Text(
      '${widget.title} > $displayText',
      style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14.0),
      overflow: TextOverflow.ellipsis,
    );
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.all(4.0),
      decoration: BoxDecoration(
        border: Border.all(color: Colors.grey),
        borderRadius: BorderRadius.circular(8.0),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 区域标题栏
          Container(
            padding: EdgeInsets.all(MediaQuery.of(context).size.width > 768 ? 8.0 : 6.0),
            decoration: BoxDecoration(
              color: Colors.grey[300],
              borderRadius: const BorderRadius.vertical(
                top: Radius.circular(8.0),
              ),
            ),
            child: Row(
              children: [
                IconButton(
                  icon: const Icon(Icons.arrow_back),
                  onPressed: _currentPath.isEmpty ? null : _navigateBack,
                  padding: EdgeInsets.zero,
                  constraints: const BoxConstraints(),
                ),
                const SizedBox(width: 4),
                // 修改区域标题显示方式，处理文字过长问题
                Expanded(child: _buildTitleText()),
                const Spacer(),
                IconButton(
                  icon: Icon(
                    widget.title == '结果'
                        ? Icons.download_for_offline_outlined
                        : Icons.upload_file,
                    size: MediaQuery.of(context).size.width > 768 ? 20 : 18,
                  ),
                  onPressed: () async {
                    if (widget.title == '结果') {
                      // 导出文件功能
                      _exportFiles();
                    } else {
                      // 导入文件功能 - 显示选项菜单
                      _showImportMenu(context);
                    }
                  },
                  padding: EdgeInsets.zero,
                  constraints: const BoxConstraints(),
                ),
                const SizedBox(width: 4),
              ],
            ),
          ),
          // 文件列表区域
          Expanded(
            child: Container(
              padding: EdgeInsets.all(MediaQuery.of(context).size.width > 768 ? 8.0 : 6.0),
              child: FutureBuilder<List<FileInfo>>(
                future: _filesFuture,
                builder: (context, snapshot) {
                  if (snapshot.connectionState == ConnectionState.waiting) {
                    return const Center(child: CircularProgressIndicator());
                  }

                  if (snapshot.hasError) {
                    return Center(child: Text('加载错误: ${snapshot.error}'));
                  }

                  final files = snapshot.data ?? [];

                  if (files.isEmpty) {
                    return const Center(
                      child: Text(
                        '暂无文件',
                        style: TextStyle(color: Colors.grey),
                      ),
                    );
                  }

                  return ListView.builder(
                    itemCount: files.length,
                    itemBuilder: (context, index) {
                      final file = files[index];
                      return _FileItem(
                        file: file,
                        currentSection: widget.title,
                        onDoubleTap: () async {
                          if (file.isDirectory) {
                            // 展开文件夹
                            _navigateToDirectory(file.name);
                          } else {
                            // 仅传输文件内容到后端处理，不直接打开文件
                            await ApiService.processFileContent(file.id);
                          }
                        },
                        onDelete: () {
                          _confirmDelete(context, file);
                        },
                        onRefresh: _refreshFiles,
                        onMoveToSection: (targetSection) async {
                          // 检查是否尝试移动结果区的文件（结果区文件具有只读属性，不允许移动）
                          bool isResultSection = file.section == 'result';
                          bool isResultTitle = widget.title == '结果';
                          if (isResultSection && isResultTitle) {
                            if (mounted) {
                              TooltipUtil.showTooltip(
                                '结果区文件不允许移动',
                                TooltipPosition.fileAreaCenter,
                              );
                            }
                            return;
                          }
                          
                          final result = await ApiService.moveFileToSection(
                            file.id,
                            targetSection,
                            _currentPath,
                          );
                          bool success = result['code'] == 200; // 提取成功状态

                          if (success) {
                            if (mounted) {
                              TooltipUtil.showTooltip(
                                '文件 "${file.name}" 已移动到 "$targetSection"',
                                TooltipPosition.fileAreaCenter,
                              );
                              // 刷新所有区域
                              widget.onFilesChanged?.call();
                            }
                          } else {
                            if (mounted) {
                              TooltipUtil.showTooltip(
                                '移动文件 "${file.name}" 失败',
                                TooltipPosition.fileAreaCenter,
                              );
                            }
                          }
                        },
                      );
                    },
                  );
                },
              ),
            ),
          ),
        ],
      ),
    );
  }

  void _confirmDelete(BuildContext context, FileInfo file) {
    showDialog(
      context: context,
      builder: (BuildContext context) {
        return AlertDialog(
          title: const Text('确认删除'),
          content: Text('确定要删除 "${file.name}" 吗？'),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(context).pop(),
              child: const Text('取消'),
            ),
            TextButton(
              onPressed: () async {
                Navigator.of(context).pop();
                
                // 检查是否尝试删除结果区的文件（结果区文件具有只读属性，不允许删除）
                bool isResultSection = file.section == 'result';
                bool isResultTitle = widget.title == '结果';
                if (isResultSection && isResultTitle) {
                  if (mounted) {
                    TooltipUtil.showTooltip(
                      '结果区文件不允许删除',
                      TooltipPosition.fileAreaCenter,
                    );
                  }
                  return;
                }
                
                final result = await ApiService.deleteFile(file.id, _currentPath);
                bool success = result['code'] == 200; // 提取成功状态
                
                if (success) {
                  if (mounted) {
                    TooltipUtil.showTooltip(
                      '删除成功',
                      TooltipPosition.fileAreaCenter,
                    );
                    _refreshFiles();

                    // 刷新所有区域
                    widget.onFilesChanged?.call();
                  }
                } else {
                  if (mounted) {
                    TooltipUtil.showTooltip(
                      '删除失败',
                      TooltipPosition.fileAreaCenter,
                    );
                  }
                }
              },
              child: const Text('确定'),
            ),
          ],
        );
      },
    );
  }

  // 新增：显示导入菜单（文件或文件夹）
  void _showImportMenu(BuildContext context) {
    showModalBottomSheet(
      context: context,
      builder: (BuildContext context) {
        return SafeArea(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              ListTile(
                leading: const Icon(Icons.file_present),
                title: const Text('导入文件'),
                onTap: () async {
                  Navigator.pop(context);
                  final success = await ApiService.importFile(
                    widget.title, // 上传到当前区域
                    _currentPath,
                  );
                  if (success) {
                    if (mounted) {
                      TooltipUtil.showTooltip(
                        '文件导入成功',
                        TooltipPosition.fileAreaCenter,
                      );
                      // 刷新文件列表
                      _refreshFiles();

                      // 刷新所有区域
                      widget.onFilesChanged?.call();
                    }
                  } else {
                    if (mounted) {
                      TooltipUtil.showTooltip(
                        '文件导入失败',
                        TooltipPosition.fileAreaCenter,
                      );
                    }
                  }
                },
              ),
            ],
          ),
        );
      },
    );
  }
}

class _FileItem extends StatefulWidget {
  final FileInfo file;
  final String currentSection;
  final VoidCallback onDoubleTap;
  final VoidCallback onDelete;
  final VoidCallback onRefresh;
  final Function(String) onMoveToSection;

  const _FileItem({
    required this.file,
    required this.currentSection,
    required this.onDoubleTap,
    required this.onDelete,
    required this.onRefresh,
    required this.onMoveToSection,
  });

  @override
  State<_FileItem> createState() => _FileItemState();
}

class _FileItemState extends State<_FileItem> {
  bool _isSelected = false;
  bool _isHovered = false; // 添加悬停状态

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onDoubleTap: widget.onDoubleTap,
      onTap: () {
        // 单击文件不产生视觉反馈
      },
      onLongPress: () {
        // 长按显示操作菜单
        _showContextMenu(context);
      },
      child: MouseRegion(
        onEnter: (_) => setState(() => _isHovered = true),
        onExit: (_) => setState(() => _isHovered = false),
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 150),
          decoration: BoxDecoration(
            color: _isHovered ? Colors.grey[200] : null, // 悬停时变灰
            borderRadius: BorderRadius.circular(4.0),
          ),
          child: ListTile(
            dense: true,
            leading: Icon(
              widget.file.isDirectory ? Icons.folder : Icons.description,
              size: MediaQuery.of(context).size.width > 768 ? 20 : 18,
            ),
            title: Text(
              widget.file.name,
              style: TextStyle(fontSize: MediaQuery.of(context).size.width > 768 ? 14 : 12),
            ),
            subtitle: Text(
              widget.file.isDirectory
                  ? '文件夹'
                  : '文件 • ${_formatFileSize(widget.file.size)}',
              style: TextStyle(fontSize: MediaQuery.of(context).size.width > 768 ? 12 : 10, color: Colors.grey),
            ),
            contentPadding: const EdgeInsets.symmetric(horizontal: 8.0),
            trailing: PopupMenuButton<String>(
              onSelected: (String value) {
                switch (value) {
                  case 'open':
                    // 对于文件夹，进入下一级；对于文件，执行双击操作
                    if (widget.file.isDirectory) {
                      // 进入文件夹
                      widget.onDoubleTap(); // 这会调用 _navigateToDirectory 方法
                    } else {
                      // 处理文件内容
                      widget.onDoubleTap(); // 这会调用 ApiService().processFileContent 方法
                    }
                    break;
                  case 'delete':
                    widget.onDelete();
                    break;
                  case 'move_to_waiting':
                    widget.onMoveToSection('等待');
                    break;
                  case 'move_to_read':
                    widget.onMoveToSection('读取');
                    break;
                  case 'move_to_template':
                    widget.onMoveToSection('模板');
                    break;
                  case 'move_to_result':
                    widget.onMoveToSection('结果');
                    break;
                }
              },
              itemBuilder: (BuildContext context) => <PopupMenuEntry<String>>[
                // 为文件和文件夹都添加打开选项
                const PopupMenuItem<String>(value: 'open', child: Text('打开')),
                const PopupMenuDivider(),
                if (widget.currentSection != '等待')
                  const PopupMenuItem<String>(
                    value: 'move_to_waiting',
                    child: Text('移动到 等待'),
                  ),
                if (widget.currentSection != '读取')
                  const PopupMenuItem<String>(
                    value: 'move_to_read',
                    child: Text('移动到 读取'),
                  ),
                if (widget.currentSection != '模板')
                  const PopupMenuItem<String>(
                    value: 'move_to_template',
                    child: Text('移动到 模板'),
                  ),
                if (widget.currentSection != '结果')
                  const PopupMenuItem<String>(
                    value: 'move_to_result',
                    child: Text('移动到 结果'),
                  ),
                const PopupMenuDivider(),
                const PopupMenuItem<String>(value: 'delete', child: Text('删除')),
              ],
            ),
          ),
        ),
      ),
    );
  }

  void _showContextMenu(BuildContext context) {
    showModalBottomSheet(
      context: context,
      builder: (BuildContext context) {
        return SafeArea(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              ListTile(
                leading: const Icon(Icons.info),
                title: const Text('属性'),
                onTap: () {
                  Navigator.pop(context);
                  _showFileDetails(context);
                },
              ),
              // 为文件和文件夹都添加打开选项
              ListTile(
                leading: const Icon(Icons.open_in_browser),
                title: const Text('打开'),
                onTap: () {
                  Navigator.pop(context);
                  widget.onDoubleTap();
                },
              ),
              ListTile(
                leading: const Icon(Icons.delete),
                title: const Text('删除'),
                onTap: () {
                  Navigator.pop(context);
                  widget.onDelete();
                },
              ),
            ],
          ),
        );
      },
    );
  }

  void _showFileDetails(BuildContext context) {
    showModalBottomSheet(
      context: context,
      builder: (BuildContext context) {
        return SafeArea(
          child: Container(
            padding: const EdgeInsets.all(16),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  widget.file.name,
                  style: const TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                const SizedBox(height: 16),
                Text('类型: ${widget.file.isDirectory ? "文件夹" : "文件"}'),
                Text('ID: ${widget.file.id}'),
                Text('区域: ${widget.file.section}'),
                // 只有文件才显示大小
                if (!widget.file.isDirectory)
                  Text('大小: ${_formatFileSize(widget.file.size)}'),
                Text('修改时间: ${_formatDateTime(widget.file.modified)}'),
              ],
            ),
          ),
        );
      },
    );
  }

  String _formatFileSize(int size) {
    if (size < 1024) {
      return '$size B';
    } else if (size < 1024 * 1024) {
      return '${(size / 1024).toStringAsFixed(2)} KB';
    } else if (size < 1024 * 1024 * 1024) {
      return '${(size / (1024 * 1024)).toStringAsFixed(2)} MB';
    } else {
      return '${(size / (1024 * 1024 * 1024)).toStringAsFixed(2)} GB';
    }
  }

  String _formatDateTime(DateTime dateTime) {
    return '${dateTime.year}-${dateTime.month.toString().padLeft(2, '0')}-${dateTime.day.toString().padLeft(2, '0')} '
        '${dateTime.hour.toString().padLeft(2, '0')}:${dateTime.minute.toString().padLeft(2, '0')}:${dateTime.second.toString().padLeft(2, '0')}';
  }
}
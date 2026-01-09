import 'package:flutter/material.dart';
import '../models/file_info.dart';
import 'tips.dart';
import '../services/api_service.dart';
import '../config/auth_config.dart';
import '../config/server_config.dart';
import 'dart:convert';
import 'dart:typed_data';
import 'dart:async';
import 'package:file_picker/file_picker.dart';
import 'package:universal_io/io.dart' as universal_io;

class FileSection extends StatefulWidget {
  final String title;
  final VoidCallback? onFilesChanged;

  const FileSection({super.key, required this.title, this.onFilesChanged});

  @override
  State<FileSection> createState() => FileSectionState();
}

class FileSectionState extends State<FileSection> {
  late Future<Map<String, dynamic>> _filesFuture;
  // 防止重复操作的标志位
  bool _isProcessing = false;

  @override
  void initState() {
    super.initState();
    _refreshFiles();
  }

  void _refreshFiles() {
    setState(() {
      _filesFuture = ApiService.getFiles();
    });
  }

  // 导出文件功能
  void _exportFiles() async {
    // 防止在处理中时导出
    if (_isProcessing) return;

    final result = await _filesFuture;
    bool hasFiles =
        result['code'] == 200 && (result['data'] as List).isNotEmpty;

    if (!hasFiles) {
      return;
    }

    final filesData = result['data'] as List;
    final files = filesData.map((item) => FileInfo.fromJson(item)).toList();

    if (files.length == 1) {
      setState(() {
        _isProcessing = true;
      });

      // 如果只有一个文件，直接下载
      _downloadFile(files.first);
    } else {
      // 如果有多个文件，让用户选择要导出的文件
      _selectFilesToExport(files);
    }
  }

  // 下载单个文件
  void _downloadFile(FileInfo file) async {
    final result = await ApiService.downloadFile(file.id);
    bool success = result['code'] == 200;

    if (success && mounted) {
      TooltipUtil.showTooltip(
        '文件下载成功',
        TooltipPosition.fileAreaCenter,
      );
    } else if (mounted) {
      TooltipUtil.showTooltip(
        '文件下载失败',
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
                      // 检查是否是结果区，如果是则跳过（结果区文件只读）
                      if (file.section == 'result') {
                        return Container(); // 不显示结果区的文件
                      }

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
                          Icons.description,
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

                        // 执行下载操作
                        bool allSuccess = true;
                        for (final file in selectedFiles) {
                          final result = await ApiService.downloadFile(file.id);
                          bool success = result['code'] == 200;

                          if (!success) {
                            allSuccess = false;
                          }
                        }

                        // 显示导出结果提示
                        if (mounted) {
                          TooltipUtil.showTooltip(
                            allSuccess ? '文件下载成功' : '部分文件下载失败',
                            TooltipPosition.fileAreaCenter,
                          );

                          setState(() {
                            _isProcessing = false;
                          });
                        }
                      }
                    },
                    child: const Text('下载'),
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
            padding: EdgeInsets.all(
                MediaQuery.of(context).size.width > 768 ? 8.0 : 6.0),
            decoration: BoxDecoration(
              color: Colors.grey[300],
              borderRadius: const BorderRadius.vertical(
                top: Radius.circular(8.0),
              ),
            ),
            child: Row(
              children: [
                // 修改区域标题显示方式，处理文字过长问题
                Expanded(
                  child: Text(
                    widget.title,
                    style: const TextStyle(
                        fontWeight: FontWeight.bold, fontSize: 14.0),
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
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
              padding: EdgeInsets.all(
                  MediaQuery.of(context).size.width > 768 ? 8.0 : 6.0),
              child: FutureBuilder<Map<String, dynamic>>(
                future: _filesFuture,
                builder: (context, snapshot) {
                  if (snapshot.connectionState == ConnectionState.waiting) {
                    return const Center(child: CircularProgressIndicator());
                  }

                  if (snapshot.hasError) {
                    return Center(child: Text('加载错误: ${snapshot.error}'));
                  }

                  if (snapshot.hasData && snapshot.data!['code'] == 200) {
                    final filesData = snapshot.data!['data'] as List;
                    final files = filesData
                        .map((item) => FileInfo.fromJson(item))
                        .toList();

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

                        // 如果文件在结果区，根据规范不显示或限制操作
                        if (widget.title == '结果' && file.section != 'result') {
                          return Container(); // 只在结果区显示结果文件
                        }

                        return _FileItem(
                          file: file,
                          currentSection: widget.title,
                          onDoubleTap: () async {
                            // 暂时不做任何操作
                          },
                          onDelete: () {
                            // 如果当前区域是结果区，则不允许删除
                            if (widget.title == '结果') {
                              if (mounted) {
                                TooltipUtil.showTooltip(
                                  '结果区文件不允许删除',
                                  TooltipPosition.fileAreaCenter,
                                );
                              }
                              return;
                            }
                            _confirmDelete(context, file);
                          },
                          onRefresh: _refreshFiles,
                          onMoveToSection: (targetSection) async {
                            // 如果当前区域是结果区，则不允许移动
                            if (widget.title == '结果') {
                              if (mounted) {
                                TooltipUtil.showTooltip(
                                  '结果区文件不允许移动',
                                  TooltipPosition.fileAreaCenter,
                                );
                              }
                              return;
                            }

                            // 调用API移动文件到目标区域
                            if (mounted) {
                              final result = await ApiService.moveFile(
                                  file.id, targetSection);
                              bool success = result['code'] == 200;

                              if (success) {
                                TooltipUtil.showTooltip(
                                  '移动成功',
                                  TooltipPosition.fileAreaCenter,
                                );
                                _refreshFiles();

                                // 刷新所有区域
                                widget.onFilesChanged?.call();
                              } else {
                                TooltipUtil.showTooltip(
                                  '移动失败',
                                  TooltipPosition.fileAreaCenter,
                                );
                              }
                            }
                          },
                        );
                      },
                    );
                  } else {
                    return const Center(
                      child: Text(
                        '获取文件列表失败',
                        style: TextStyle(color: Colors.grey),
                      ),
                    );
                  }
                },
              ),
            ),
          ),
        ],
      ),
    );
  }

  void _confirmDelete(BuildContext context, FileInfo file) {
    // 如果文件在结果区，则不允许删除
    if (file.section == 'result') {
      if (mounted) {
        TooltipUtil.showTooltip(
          '结果区文件不允许删除',
          TooltipPosition.fileAreaCenter,
        );
      }
      return;
    }

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

                final result = await ApiService.deleteFile(file.id);
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

  // 新增：显示导入菜单（文件）
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
                  _selectAndUploadFile();
                },
              ),
            ],
          ),
        );
      },
    );
  }

  // 选择并上传文件
  void _selectAndUploadFile() async {
    try {
      final result = await FilePicker.platform.pickFiles(
        allowMultiple: true,
        type: FileType.custom,
        allowedExtensions: [
          'pdf',
          'doc',
          'docx',
          'txt',
          'xlsx',
          'xls',
          'ppt',
          'pptx',
          'jpg',
          'jpeg',
          'png',
          'gif',
          'md'
        ],
      );

      if (result != null && result.files.isNotEmpty) {
        for (int i = 0; i < result.files.length; i++) {
          PlatformFile platformFile = result.files[i];

          if (platformFile.bytes != null) {
            await _uploadFileToServer(platformFile);
          } else if (platformFile.path != null) {
            // 在某些平台上，文件可能不会直接加载到内存中
            await _uploadFileAtPath(platformFile);
          }
        }
      }
    } catch (e) {
      if (mounted) {
        TooltipUtil.showTooltip(
          '文件选择失败',
          TooltipPosition.fileAreaCenter,
        );
      }
    }
  }

  // 上传文件到服务器（来自bytes）
  Future<void> _uploadFileToServer(PlatformFile file) async {
    try {
      // 显示上传进度提示
      if (mounted) {
        TooltipUtil.showTooltip(
          '正在上传: ${file.name}',
          TooltipPosition.fileAreaCenter,
        );
      }

      // 调用现有的API服务上传文件
      final result = await ApiService.uploadFile(file, file.name);

      if (result['code'] == 200 && mounted) {
        TooltipUtil.showTooltip(
          '上传成功: ${file.name}',
          TooltipPosition.fileAreaCenter,
        );
        // 刷新文件列表
        _refreshFiles();
      } else if (mounted) {
        TooltipUtil.showTooltip(
          '上传失败: ${file.name}',
          TooltipPosition.fileAreaCenter,
        );
      }
    } catch (e) {
      if (mounted) {
        TooltipUtil.showTooltip(
          '上传异常: ${file.name}',
          TooltipPosition.fileAreaCenter,
        );
      }
    }
  }

  // 上传文件到服务器（来自路径）
  Future<void> _uploadFileAtPath(PlatformFile file) async {
    try {
      // 显示上传进度提示
      if (mounted) {
        TooltipUtil.showTooltip(
          '正在上传: ${file.name}',
          TooltipPosition.fileAreaCenter,
        );
      }

      // 从路径创建文件对象
      if (file.path != null) {
        final actualFile = universal_io.File(file.path!);

        // 调用现有的API服务上传文件
        final result = await ApiService.uploadFile(actualFile, file.name);

        if (result['code'] == 200 && mounted) {
          TooltipUtil.showTooltip(
            '上传成功: ${file.name}',
            TooltipPosition.fileAreaCenter,
          );
          // 刷新文件列表
          _refreshFiles();
        } else if (mounted) {
          TooltipUtil.showTooltip(
            '上传失败: ${file.name}',
            TooltipPosition.fileAreaCenter,
          );
        }
      }
    } catch (e) {
      if (mounted) {
        TooltipUtil.showTooltip(
          '上传异常: ${file.name}',
          TooltipPosition.fileAreaCenter,
        );
      }
    }
  }
}

// 将State类改为公开的，以便外部可以引用
typedef FileItemState = _FileItemState;

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
  bool _isHovered = false; // 添加悬停状态

  @override
  Widget build(BuildContext context) {
    // 如果当前文件在结果区，根据规范应该限制某些操作
    bool isResultFile = widget.file.section == 'result';

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
              Icons.description,
              size: MediaQuery.of(context).size.width > 768 ? 20 : 18,
            ),
            title: Text(
              widget.file.name,
              style: TextStyle(
                  fontSize: MediaQuery.of(context).size.width > 768 ? 14 : 12),
            ),
            subtitle: Text(
              '文件 • ${_formatFileSize(widget.file.size)}',
              style: TextStyle(
                  fontSize: MediaQuery.of(context).size.width > 768 ? 12 : 10,
                  color: Colors.grey),
            ),
            contentPadding: const EdgeInsets.symmetric(horizontal: 8.0),
            trailing: PopupMenuButton<String>(
              onSelected: (String value) {
                switch (value) {
                  case 'open':
                    // 处理文件内容
                    widget.onDoubleTap();
                    break;
                  case 'delete':
                    // 如果是结果区文件，则不允许删除
                    if (isResultFile) {
                      if (mounted) {
                        TooltipUtil.showTooltip(
                          '结果区文件不允许删除',
                          TooltipPosition.fileAreaCenter,
                        );
                      }
                    } else {
                      widget.onDelete();
                    }
                    break;
                  case 'move_to_waiting':
                    // 如果是结果区文件，则不允许移动
                    if (isResultFile) {
                      if (mounted) {
                        TooltipUtil.showTooltip(
                          '结果区文件不允许移动',
                          TooltipPosition.fileAreaCenter,
                        );
                      }
                    } else {
                      widget.onMoveToSection('等待');
                    }
                    break;
                  case 'move_to_read':
                    // 如果是结果区文件，则不允许移动
                    if (isResultFile) {
                      if (mounted) {
                        TooltipUtil.showTooltip(
                          '结果区文件不允许移动',
                          TooltipPosition.fileAreaCenter,
                        );
                      }
                    } else {
                      widget.onMoveToSection('读取');
                    }
                    break;
                  case 'move_to_template':
                    // 如果是结果区文件，则不允许移动
                    if (isResultFile) {
                      if (mounted) {
                        TooltipUtil.showTooltip(
                          '结果区文件不允许移动',
                          TooltipPosition.fileAreaCenter,
                        );
                      }
                    } else {
                      widget.onMoveToSection('模板');
                    }
                    break;
                  case 'move_to_result':
                    // 如果是结果区文件，则不允许移动
                    if (isResultFile) {
                      if (mounted) {
                        TooltipUtil.showTooltip(
                          '结果区文件不允许移动',
                          TooltipPosition.fileAreaCenter,
                        );
                      }
                    } else {
                      widget.onMoveToSection('结果');
                    }
                    break;
                }
              },
              itemBuilder: (BuildContext context) => <PopupMenuEntry<String>>[
                // 为文件添加打开选项
                const PopupMenuItem<String>(value: 'open', child: Text('打开')),
                const PopupMenuDivider(),
                if (widget.currentSection != '等待' && !isResultFile)
                  const PopupMenuItem<String>(
                    value: 'move_to_waiting',
                    child: Text('移动到 等待'),
                  ),
                if (widget.currentSection != '读取' && !isResultFile)
                  const PopupMenuItem<String>(
                    value: 'move_to_read',
                    child: Text('移动到 读取'),
                  ),
                if (widget.currentSection != '模板' && !isResultFile)
                  const PopupMenuItem<String>(
                    value: 'move_to_template',
                    child: Text('移动到 模板'),
                  ),
                const PopupMenuDivider(),
                // 如果是结果区文件，则不显示删除选项
                if (!isResultFile)
                  const PopupMenuItem<String>(
                      value: 'delete', child: Text('删除')),
              ],
            ),
          ),
        ),
      ),
    );
  }

  void _showContextMenu(BuildContext context) {
    bool isResultFile = widget.file.section == 'result';

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
              // 为文件添加打开选项
              ListTile(
                leading: const Icon(Icons.open_in_browser),
                title: const Text('打开'),
                onTap: () {
                  Navigator.pop(context);
                  widget.onDoubleTap();
                },
              ),
              // 如果是结果区文件，则不显示删除选项
              if (!isResultFile)
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
        bool isResultFile = widget.file.section == 'result';

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
                Text('类型: 文件'),
                Text('ID: ${widget.file.id}'),
                Text('区域: ${widget.file.section}'),
                Text('大小: ${_formatFileSize(widget.file.size)}'),
                Text('修改时间: ${_formatDateTime(widget.file.modified)}'),
                // 如果是结果区文件，添加特殊标记
                if (isResultFile)
                  const Text(
                    '注意: 此文件在结果区，不允许删除或移动',
                    style: TextStyle(color: Colors.orange),
                  ),
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

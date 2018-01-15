package org.nlpcn.jcoder.util;

import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.nlpcn.jcoder.domain.ApiDoc;
import org.nlpcn.jcoder.domain.ClassDoc;
import org.nlpcn.jcoder.domain.Task;
import org.nlpcn.jcoder.run.java.JavaRunner;
import org.nlpcn.jcoder.run.java.JavaSourceUtil;
import org.nlpcn.jcoder.service.TaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 监听文件和文件夹变化
 */
public class GroupFileListener extends FileAlterationListenerAdaptor {

	private static final Logger LOG = LoggerFactory.getLogger(GroupFileListener.class);

	private static final ConcurrentHashMap<String, FileAlterationMonitor> MAP = new ConcurrentHashMap<>();

	/**
	 * 注册一个监听事件
	 */
	public static void regediter(String groupName) {
		GroupFileListener groupFileListener = new GroupFileListener(groupName);

		groupFileListener.init();

		FileAlterationObserver observer = new FileAlterationObserver(new File(StaticValue.GROUP_FILE, groupName + "/src/api"), null, null);
		observer.addListener(groupFileListener);
		FileAlterationMonitor monitor = new FileAlterationMonitor(100, observer);
		// 开始监控
		MAP.put(groupName, monitor);
		try {
			monitor.start();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * 初始化做一次同步
	 */
	private void init() {
		try {

			List<File> allApi = new ArrayList<>();

			File srcFile = new File(StaticValue.GROUP_FILE, groupName + "/src/api");

			if (!srcFile.exists()) {
				srcFile.mkdirs();
			}

			Files.walkFileTree(srcFile.toPath(), new SimpleFileVisitor<Path>() {

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					if (file.toFile().getName().endsWith(".java")) {
						allApi.add(file.toFile());
					}
					return FileVisitResult.CONTINUE;
				}
			});

			Map<String, Task> maps = new HashMap<>();

			StaticValue.getSystemIoc().get(TaskService.class, "taskService").findTaskByGroupNameCache(groupName).forEach(t -> {
				maps.put(t.getName(), t);
			});


			allApi.stream().forEach(file -> {
				String taskName = taskName(file);
				Task task = getTask(taskName);
				if (task != null) {
					maps.remove(task.getName());
					this.onFileChange(file);
				} else {
					this.onFileCreate(file);
				}
			});

			maps.values().forEach(t -> {
				try {
					writeTask2Src(t);
				} catch (IOException e) {
					e.printStackTrace();
				}

			});

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void writeTask2Src(Task t) throws IOException {
		LOG.info("syn write file by task " + t.getName());
		String pk = JavaSourceUtil.findPackage(t.getCode());
		File file = new File(StaticValue.GROUP_FILE, t.getGroupName() + "/src/api/" + pk.replace(".", "/") + "/" + t.getName() + ".java");

		if (!file.getParentFile().exists()) {
			file.getParentFile().mkdirs();

		}
		try (FileOutputStream fos = new FileOutputStream(file)) {
			fos.write(t.getCode().getBytes("utf-8"));
		}
	}

	/**
	 * 注销一个监听事件
	 */
	public static void unRegediter(String groupName) {
		FileAlterationMonitor remove = MAP.remove(groupName);
		if (remove != null) {
			try {
				remove.stop();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private String groupName;

	public GroupFileListener(String groupName) {
		this.groupName = groupName;
	}

	@Override
	public void onFileCreate(File file) {
		if (file.getName().endsWith(".java")) {
			LOG.info("[新建]:" + file.getAbsolutePath());

			String content = IOUtil.getContent(file, "utf-8");

			try {

				List<ApiDoc> sub = null;
				if (!StringUtil.isBlank(content)) {
					ClassDoc parse = JavaDocUtil.parse(new StringReader(content));
					if (parse != null) {
						sub = parse.getSub();
					}
				}


				if (sub == null || sub.size() == 0) {
					LOG.error("...............................................................................................................................");
					LOG.error("...............................................................................................................................");
					LOG.error("...............................................................................................................................");
					LOG.error("...............................................................................................................................");
					LOG.error("...............................................................................................................................");
					LOG.error(file.getCanonicalPath() + "  not found any api  ...................................................................");
					LOG.error("...............................................................................................................................");
					LOG.error("...............................................................................................................................");
					LOG.error("...............................................................................................................................");
					LOG.error("...............................................................................................................................");
					LOG.error("...............................................................................................................................");
					return;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

			Task task = getTask(taskName(file));

			if (task != null) {
				if (task.getCode().equals(content)) {
					return;
				}
				try {
					LOG.error("...............................................................................................................................");
					LOG.error("...............................................................................................................................");
					LOG.error("...............................................................................................................................");
					LOG.error("...............................................................................................................................");
					LOG.error("...............................................................................................................................");
					LOG.error(task.getName() + "  已经存在你重名了........task name all ready in ...................................................................");
					LOG.error("==========" + JavaSourceUtil.findPackage(task.getCode()));
					LOG.error("...............................................................................................................................");
					LOG.error("...............................................................................................................................");
					LOG.error("...............................................................................................................................");
					LOG.error("...............................................................................................................................");
					LOG.error("...............................................................................................................................");
				} catch (IOException e) {
					e.printStackTrace();
				}
			} else {
				task = new Task();
				task.setCode(IOUtil.getContent(file, "utf-8"));
				task.setCreateUser("admin");
				task.setType(1);
				task.setStatus(1);
				task.setCreateTime(new Date());
				task.setUpdateTime(new Date());
				task.setGroupName(groupName);
				task.setDescription("file create");
				try {
					StaticValue.getSystemIoc().get(TaskService.class, "taskService").saveOrUpdate(task);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	@Override
	public void onFileChange(File file) {
		if (file.getName().endsWith(".java")) {
			Task task = getTask(taskName(file));

			if (task == null) {
				onFileCreate(file);
				return;
			} else {

				String fCode = IOUtil.getContent(file, "utf-8");

				if (fCode.equals(task.getCode())) {
					return;
				}

				LOG.info("[修改]:" + file.getAbsolutePath());
				task.setCode(fCode);


				try {
					//try compile
					new JavaRunner(task).compile();
					try {
						StaticValue.getSystemIoc().get(TaskService.class, "taskService").saveOrUpdate(task);
					} catch (Exception e) {
						e.printStackTrace();
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

		}
	}


	@Override
	public void onFileDelete(File file) {
		if (file.getName().endsWith(".java")) {
			System.out.println("[删除]:" + file.getAbsolutePath());

			Task task = getTask(taskName(file));

			if (task != null) {
				try {
					StaticValue.getSystemIoc().get(TaskService.class, "taskService").delete(task);
					StaticValue.getSystemIoc().get(TaskService.class, "taskService").delByDB(task);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

		}
	}


	private String taskName(File file) {
		return file.getName().substring(0, file.getName().length() - 5);
	}


	private Task getTask(String taskName) {
		Task task = StaticValue.getSystemIoc().get(TaskService.class, "taskService").findTask(groupName, taskName);
		return task;
	}

}
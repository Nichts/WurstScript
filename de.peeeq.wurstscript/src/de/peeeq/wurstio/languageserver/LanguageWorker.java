package de.peeeq.wurstio.languageserver;

import de.peeeq.wurstio.languageserver.requests.GetCompletions;
import de.peeeq.wurstio.languageserver.requests.GetDefinition;
import de.peeeq.wurstio.languageserver.requests.HoverInfo;
import de.peeeq.wurstio.languageserver.requests.UserRequest;
import de.peeeq.wurstscript.WLogger;
import de.peeeq.wurstscript.utils.Utils;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicLong;

public class LanguageWorker implements Runnable {

	private final Map<String, PendingChange> changes = new LinkedHashMap<>();
	private final AtomicLong currentTime = new AtomicLong();
	private final LanguageServer server;
	private final Queue<UserRequest> userRequests = new LinkedList<>();

	private ModelManager modelManager;
	private String rootPath;

	private final Object lock = new Object();

	public LanguageWorker(LanguageServer server) {
		this.server = server;
	}

	public void handleFileChanged(String filePath) {
		if (!Utils.isWurstFile(filePath)
				&& !filePath.endsWith("wurst.dependencies")) {
			// ignore all files which are not relevant for wurst
			return;
		}

		WLogger.info("handle handleFileChanged " + filePath);
		synchronized (lock) {
			changes.put(filePath, new FileUpdated(filePath));
			lock.notifyAll();
		}
		WLogger.info("handle handleFileChanged END " + filePath);
	}

	public void handleInit(String rootPath) {
		WLogger.info("handle init " + rootPath);
		synchronized (lock) {
			this.rootPath = rootPath;
			this.modelManager = null;
			lock.notifyAll();
		}
		WLogger.info("handle init END " + rootPath);
	}

	public void handleReconcile(String file, String content) {
		synchronized (lock) {
			changes.put(file, new FileReconcile(file, content));
			lock.notifyAll();
		}
	}

	public void handleGetDefinition(int sequenceNr, String filename, String buffer, int line, int column) {
		synchronized (lock) {
			userRequests.removeIf(req -> req instanceof  GetDefinition);
			userRequests.add(new GetDefinition(sequenceNr, filename, buffer, line, column));
			lock.notifyAll();
		}
	}

	public void handleGetHoverInfo(int sequenceNr, String filename, String buffer, int line, int column) {
		synchronized (lock) {
			userRequests.removeIf(req -> req instanceof HoverInfo);
			userRequests.add(new HoverInfo(sequenceNr, filename, buffer, line, column));
			lock.notifyAll();
		}
	}

	public void handleGetCompletions(int sequenceNr, String filename, String buffer, int line, int column) {
		synchronized (lock) {
			userRequests.removeIf(req -> req instanceof GetCompletions);
			userRequests.add(new GetCompletions(sequenceNr, filename, buffer, line, column));
			lock.notifyAll();
		}
	}


	private abstract class PendingChange {
		private long time;
		private String filename;

		public PendingChange(String filename) {
			time = currentTime.incrementAndGet();
			this.filename = filename;
		}

		public long getTime() {
			return time;
		}

		public String getFilename() {
			return filename;
		}
	}

	private class FileUpdated extends PendingChange {

		public FileUpdated(String filename) {
			super(filename);
		}
	}

	private class FileDeleted extends PendingChange {

		public FileDeleted(String filename) {
			super(filename);
		}
	}

	private class FileReconcile extends PendingChange {

		private String contents;

		public FileReconcile(String filename, String contents) {
			super(filename);
			this.contents = contents;
		}

		public String getContents() {
			return contents;
		}
	}


	@Override
	public void run() {
		try {
			while (!Thread.currentThread().isInterrupted()) {
				Runnable work;
				synchronized (lock) {
					work = getNextWorkItem();
					if (work == null) {
						lock.wait(10000);
						work = getNextWorkItem();
					}
				}
				if (work != null) {
					// actual work is not synchronized, so that requests can come in while the work is done
					try {
						work.run();
					} catch (Exception e) {
						WLogger.severe(e);
						System.err.println("Error in request " + work + " (see log for details): " + e.getMessage());
					}
				}
			}
		} catch (InterruptedException e) {
			// ignore
		}
		WLogger.info("Language Worker interrupted");
	}

	private Runnable getNextWorkItem() {
		if (modelManager == null) {
			if (rootPath != null) {
				return () -> doInit(rootPath);
			} else {
				// cannot do anything useful at the moment
				WLogger.info("LanguageWorker is waiting for init ... ");
			}
		} else if (!userRequests.isEmpty()) {
			UserRequest req = userRequests.remove();
			Object response = req.execute(modelManager);
			server.reply(req.getRequestNr(), response);
		} else if (!changes.isEmpty()) {
			// TODO this can be done more efficiently than doing one at a time
			PendingChange change = removeFirst(changes);
			return () -> {
				if (change instanceof FileDeleted) {
					modelManager.removeCompilationUnit(change.getFilename());
				} else if (change instanceof FileUpdated) {
					modelManager.syncCompilationUnit(change.getFilename());
				} else if (change instanceof FileReconcile) {
					FileReconcile fr = (FileReconcile) change;
					modelManager.syncCompilationUnitContent(fr.getFilename(), fr.getContents());

				} else {
					WLogger.info("unhandled change request: " + change);
				}
			};
		}
		return null;
	}


	private PendingChange removeFirst(Map<String, PendingChange> changes) {
		Iterator<Entry<String, PendingChange>> it = changes.entrySet().iterator();
		Entry<String, PendingChange> e = it.next();
		it.remove();
		return e.getValue();
	}


	private void doInit(String rootPath) {
		try {
			log("Handle init " + rootPath);
			modelManager = new ModelManagerImpl(rootPath);
			modelManager.onCompilationResult(server::onCompilationResult);

			log("Start building " + rootPath);
			modelManager.buildProject();
			log("Finished building " + rootPath);
		} catch (Exception e) {
			WLogger.severe(e);
		}
	}

	private void log(String s) {
		WLogger.info(s);
	}
}
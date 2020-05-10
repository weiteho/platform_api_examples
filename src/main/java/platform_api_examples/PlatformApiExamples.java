package platform_api_examples;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.LockSupport;

import com.openfin.desktop.DesktopConnection;
import com.openfin.desktop.DesktopException;
import com.openfin.desktop.DesktopIOException;
import com.openfin.desktop.DesktopStateListener;
import com.openfin.desktop.Identity;
import com.openfin.desktop.LayoutContentItemOptions;
import com.openfin.desktop.LayoutContentItemStateOptions;
import com.openfin.desktop.LayoutContentOptionsImpl;
import com.openfin.desktop.LayoutOptions;
import com.openfin.desktop.RuntimeConfiguration;
import com.openfin.desktop.WindowOptions;
import com.openfin.desktop.platform.Platform;
import com.openfin.desktop.platform.PlatformOptions;
import com.openfin.desktop.platform.PlatformSnapshot;
import com.openfin.desktop.platform.PlatformSnapshotOptions;
import com.openfin.desktop.platform.PlatformViewOptions;
import com.openfin.desktop.platform.PlatformWindowOptions;

public class PlatformApiExamples implements DesktopStateListener {

	private DesktopConnection desktopConnection;
	private Thread callingThread;
	private String uuidForStoredSnapshot;

	PlatformApiExamples(Thread callingThread) throws DesktopException, DesktopIOException, IOException {
		this.callingThread = callingThread;
		RuntimeConfiguration runtimeConfiguration = new RuntimeConfiguration();
		runtimeConfiguration.setRuntimeVersion("canary");
		runtimeConfiguration.setAdditionalRuntimeArguments("--v=1");
		this.desktopConnection = new DesktopConnection(UUID.randomUUID().toString());
		this.desktopConnection.connect(runtimeConfiguration, this, 60);
	}

	void dispose() throws DesktopException {
		this.desktopConnection.disconnect();
	}

	CompletableFuture<?> startFromManifestThenSaveSnapshot() {
		System.out.println("startFromManifestThenSaveSnapshot......");
		return Platform.startFromManifest(desktopConnection, "https://openfin.github.io/golden-prototype/public.json")
				.thenComposeAsync(platform -> {
					CompletableFuture<?> platformClosedFuture = new CompletableFuture<>();
					platform.addEventListener("closed", a -> {
						System.out.println("Platform created from startFromManifestThenSaveSnapshot["
								+ platform.getUuid() + "] closed.");
						platformClosedFuture.complete(null);
					});

					try {
						Thread.sleep(10000);
					}
					catch (InterruptedException e) {
						e.printStackTrace();
					}

					platform.getSnapshot().thenAcceptAsync(snapshot -> {
						try {
							Files.write(Paths.get("./snapshot.json"), snapshot.toString().getBytes(),
									StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
									StandardOpenOption.WRITE);
							this.uuidForStoredSnapshot = platform.getUuid();
							System.out.println("current snapshot saved.");
						}
						catch (IOException e) {
							e.printStackTrace();
						}
					}).exceptionally(e -> {
						System.out.println("error saving snapshot: " + e.getMessage());
						return null;
					});
					return platformClosedFuture;
				});
	}

	CompletableFuture<Void> startAndApplyStoredSnapshot() {
		if (this.uuidForStoredSnapshot != null) {
			System.out.println("startAndApplyStoredSnapshot......");
			PlatformOptions platformOptions = new PlatformOptions(uuidForStoredSnapshot);
			return Platform.start(desktopConnection, platformOptions).thenComposeAsync(platform -> {
				CompletableFuture<Void> platformClosedFuture = new CompletableFuture<>();
				platform.addEventListener("closed", a -> {
					System.out.println(
							"Platform created from startAndApplyStoredSnapshot[" + platform.getUuid() + "] closed.");
					platformClosedFuture.complete(null);
				});
				PlatformSnapshotOptions opts = new PlatformSnapshotOptions();
				opts.setCloseExistingWindows(true);
				URI fileUri = Paths.get("./snapshot.json").toAbsolutePath().normalize().toUri();
				platform.applySnapshot(fileUri.toString(), opts);
				return platformClosedFuture;
			});
		}
		else {
			return CompletableFuture.completedFuture(null);
		}
	}

	CompletableFuture<Void> startAndCreateWindow() {
		System.out.println("startAndCreateWindow......");
		PlatformOptions platformOptions = new PlatformOptions(UUID.randomUUID().toString());
		PlatformWindowOptions defaultWinOpts = new PlatformWindowOptions();
		defaultWinOpts.setAutoShow(true);
		defaultWinOpts.setDefaultCentered(true);
		defaultWinOpts.setDefaultWidth(800);
		defaultWinOpts.setDefaultHeight(600);
		platformOptions.setDefaultWindowOptions(defaultWinOpts);
		return Platform.start(desktopConnection, platformOptions).thenComposeAsync(platform -> {
			CompletableFuture<Void> platformClosedFuture = new CompletableFuture<>();
			platform.addEventListener("closed", a -> {
				System.out.println("Platform created from startAndCreateWindow[" + platform.getUuid() + "] closed.");
				platformClosedFuture.complete(null);
			});
			WindowOptions winOpts = new WindowOptions();
			winOpts.setName("google");
			winOpts.setUrl("http://www.google.com");
			platform.createWindow(winOpts);
			return platformClosedFuture;
		});
	}

	CompletableFuture<Void> startAndCreateViewThenCloseView() {
		System.out.println("startAndCreateViewThenCloseView......");
		String uuid = UUID.randomUUID().toString();
		PlatformOptions platformOptions = new PlatformOptions(uuid);
		return Platform.start(desktopConnection, platformOptions).thenComposeAsync(platform -> {
			CompletableFuture<Void> platformClosedFuture = new CompletableFuture<>();
			platform.addEventListener("closed", a -> {
				System.out.println(
						"Platform created from startAndCreateViewThenCloseView[" + platform.getUuid() + "] closed.");
				platformClosedFuture.complete(null);
			});

			PlatformViewOptions viewOpts = new PlatformViewOptions();
			viewOpts.setName("google");
			viewOpts.setUrl("http://www.google.com");
			platform.createView(viewOpts, null).thenAcceptAsync(view -> {
				try {
					Thread.sleep(10000);
				}
				catch (InterruptedException e) {
					e.printStackTrace();
				}
				finally {
					platform.closeView(view);
				}
			});
			return platformClosedFuture;
		});
	}

	CompletableFuture<Void> startWithHandCraftedSnapshot() {
		System.out.println("startWithHandCraftedSnapshot......");
		String uuid = UUID.randomUUID().toString();
		PlatformOptions platformOptions = new PlatformOptions(uuid);
		return Platform.start(desktopConnection, platformOptions).thenComposeAsync(platform -> {
			CompletableFuture<Void> platformClosedFuture = new CompletableFuture<>();
			platform.addEventListener("closed", a -> {
				System.out.println(
						"Platform created from startWithHandCraftedSnapshot[" + platform.getUuid() + "] closed.");
				platformClosedFuture.complete(null);
			});
			LayoutContentItemStateOptions itemState1 = new LayoutContentItemStateOptions();
			itemState1.setName("viewOpenFin");
			itemState1.setUrl("https://www.openfin.co");
			LayoutContentItemOptions itemOpts1 = new LayoutContentItemOptions();
			itemOpts1.setType("component");
			itemOpts1.setComponentName("view");
			itemOpts1.setTitle("Test Snapshot - OpenFin");
			itemOpts1.setLayoutContentItemStateOptions(itemState1);

			LayoutContentItemStateOptions itemState2 = new LayoutContentItemStateOptions();
			itemState2.setName("viewGoogle");
			itemState2.setUrl("https://www.google.com");
			LayoutContentItemOptions itemOpts2 = new LayoutContentItemOptions();
			itemOpts2.setType("component");
			itemOpts2.setComponentName("view");
			itemOpts2.setTitle("Test Snapshot - Google");
			itemOpts2.setLayoutContentItemStateOptions(itemState2);

			LayoutContentOptionsImpl content = new LayoutContentOptionsImpl();
			content.setType("stack");
			content.setContent(itemOpts1, itemOpts2);

			LayoutOptions layoutOptions = new LayoutOptions();
			layoutOptions.setContent(content);

			WindowOptions winOpts = new WindowOptions();
			winOpts.setLayoutOptions(layoutOptions);

			PlatformSnapshot codedSnapshot = new PlatformSnapshot();
			codedSnapshot.setWindows(winOpts);
			platform.applySnapshot(codedSnapshot, null);

			return platformClosedFuture;
		});
	}
	
	CompletableFuture<Void> startAndCreateViewsThenReparentView() {
		System.out.println("startAndCreateViewsThenReparentView......");
		String uuid = UUID.randomUUID().toString();
		PlatformOptions platformOptions = new PlatformOptions(uuid);
		return Platform.start(desktopConnection, platformOptions).thenComposeAsync(platform -> {
			CompletableFuture<Void> platformClosedFuture = new CompletableFuture<>();
			platform.addEventListener("closed", a -> {
				System.out.println(
						"Platform created from startAndCreateViewsThenReparentView[" + platform.getUuid() + "] closed.");
				platformClosedFuture.complete(null);
			});
			
			PlatformViewOptions viewOpts1 = new PlatformViewOptions();
			viewOpts1.setName("openfin");
			viewOpts1.setUrl("http://www.openfin.co");
			
			CompletableFuture<Identity> view1WindowIdentityFuture = platform.createView(viewOpts1, null)
					.thenComposeAsync(view -> {
						return view.getCurrentWindow();
					}).thenApplyAsync(win -> {
						return win.getIdentity();
					});

			PlatformViewOptions viewOpts2 = new PlatformViewOptions();
			viewOpts2.setName("google");
			viewOpts2.setUrl("http://www.google.com");
			platform.createView(viewOpts2, null).thenApplyAsync(view -> {
				try {
					Thread.sleep(10000);
				}
				catch (InterruptedException e) {
					e.printStackTrace();
				}
				return view.getIdentity();
			}).thenCombineAsync(view1WindowIdentityFuture, (viewIdentity, targetIdentity)->{
				platform.reparentView(viewIdentity, targetIdentity);
				return null;
			});
			return platformClosedFuture;
		});
	}


	@Override
	public void onReady() {
		// do not block the the thread in event callbacks, do things in different
		// threads.
		CompletableFuture.runAsync(() -> {
			try {
				this.startFromManifestThenSaveSnapshot().get();
				this.startAndApplyStoredSnapshot().get();
				this.startAndCreateWindow().get();
				this.startAndCreateViewThenCloseView().get();
				this.startWithHandCraftedSnapshot().get();
				this.startAndCreateViewsThenReparentView().get();

				this.desktopConnection.disconnect();
			}
			catch (InterruptedException | ExecutionException | DesktopException e) {
				e.printStackTrace();
			}
			finally {

			}
		});
	}

	@Override
	public void onClose(String error) {
		LockSupport.unpark(this.callingThread);
	}

	@Override
	public void onError(String reason) {
	}

	@Override
	public void onMessage(String message) {
	}

	@Override
	public void onOutgoingMessage(String message) {
	}

	public static void main(String[] args) {
		try {
			new PlatformApiExamples(Thread.currentThread());
			LockSupport.park();
		}
		catch (DesktopException | DesktopIOException | IOException e) {
			e.printStackTrace();
		}
	}
}

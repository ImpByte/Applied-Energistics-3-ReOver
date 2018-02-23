package appeng.core.me.api.network;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.function.Supplier;

public interface GlobalNBDManager {

	/*
	 * Networks
	 */

	@Nonnull
	Optional<Network> getNetwork(@Nonnull NetworkUUID uuid);

	/**
	 * Called whenever a new network is created, anywhere, by anyone.<br>
	 * All 3rd party addons tinkering with networks must invoke this method as well.
	 *
	 * @param network newly created network
	 * @param <N>     network type
	 * @return the same network as argument, for chaining
	 */
	<N extends Network> N networkCreated(N network);

	Network createDefaultNetwork(NetworkUUID uuid);

	void networkDestroyed(NetworkUUID uuid);

	/*
	 * Network-Free blocks
	 */

	Optional<NetBlock> getFreeBlock(NetBlockUUID uuid);
	void registerFreeBlock(NetBlock block);
	void removeFreeBlock(NetBlock block);

	default Optional<NetBlock> getNetblock(@Nonnull Optional<NetBlockUUID> buuidO, @Nonnull Optional<NetworkUUID> nuuidO){
		return buuidO.flatMap(buuid -> nuuidO.flatMap(this::getNetwork).map(network -> network.getBlock(buuid)).orElseGet(() -> getFreeBlock(buuid)));
	}

	/*
	 * Block-Free devices
	 */

	<N extends NetDevice<N, P>, P extends PhysicalDevice<N, P>> Optional<N> getFreeDevice(DeviceUUID uuid);
	<N extends NetDevice<N, P>, P extends PhysicalDevice<N, P>> void registerFreeDevice(N device);
	<N extends NetDevice<N, P>, P extends PhysicalDevice<N, P>> void removeFreeDevice(N device);

	default <N extends NetDevice<N, P>, P extends PhysicalDevice<N, P>> Optional<N> getDevice(@Nonnull Optional<DeviceUUID> duuidO, @Nonnull Optional<NetBlockUUID> buuidO, @Nonnull Optional<NetworkUUID> nuuidO){
		return duuidO.flatMap(duuid -> getNetblock(buuidO, nuuidO).map(netBlock -> netBlock.<N, P>getDevice(duuid)).orElseGet(() -> getFreeDevice(duuid)));
	}

	/*
	 * Physical device loading access
	 */

	@Nonnull
	default <N extends NetDevice<N, P>, P extends PhysicalDevice<N, P>> N locateOrCreateNetworkCounterpart(@Nonnull Optional<DeviceUUID> duuidO, @Nonnull Optional<NetBlockUUID> buuidO, @Nonnull Optional<NetworkUUID> nuuidO, @Nonnull Supplier<N> creator){
		return this.<N, P>getDevice(duuidO, buuidO, nuuidO).orElseGet(() -> {
			N nd = creator.get();
			processCreatedDevice(nd);
			return nd;
		});
	}

	default void processCreatedBlock(@Nonnull NetBlock netBlock){
		registerFreeBlock(netBlock);
	}

	default void processDestroyedBlock(@Nonnull NetBlock netBlock){
		if(netBlock.getNetwork().isPresent()) netBlock.getNetwork().get().removeDestroyedBlock(netBlock);
		else registerFreeBlock(netBlock);
	}

	default <N extends NetDevice<N, P>, P extends PhysicalDevice<N, P>> void processCreatedDevice(@Nonnull N device){
		registerFreeDevice(device);
	}

	default <N extends NetDevice<N, P>, P extends PhysicalDevice<N, P>> void processDestroyedDevice(@Nonnull N device){
		if(device.getNetBlock().isPresent()) device.getNetBlock().get().removeDestroyedDevice(device);
		else removeFreeDevice(device);
	}

}
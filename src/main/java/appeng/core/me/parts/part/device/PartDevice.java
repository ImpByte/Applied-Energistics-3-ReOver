package appeng.core.me.parts.part.device;

import appeng.core.me.AppEngME;
import appeng.core.me.api.network.*;
import appeng.core.me.api.network.block.ConnectUUID;
import appeng.core.me.api.parts.VoxelPosition;
import appeng.core.me.api.parts.container.PartsAccess;
import appeng.core.me.network.device.NetDeviceBase;
import appeng.core.me.parts.part.PartBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;

public abstract class PartDevice<P extends PartDevice<P, S, N>, S extends PartDevice.PartDeviceState<P, S, N>, N extends NetDeviceBase<N, S>> extends PartBase<P, S> {

	public PartDevice(){
	}

	public PartDevice(boolean supportsRotation){
		super(supportsRotation);
	}

	@Override
	public void onPlaced(@Nullable S part, @Nonnull PartsAccess.Mutable world, @Nullable World theWorld, @Nullable EntityPlayer placer, @Nullable EnumHand hand){
		AppEngME.INSTANCE.getGlobalNBDManager().processCreatedDevice(part.networkCounterpart = part.createNewNetworkCounterpart());
	}

	@Override
	public void onBroken(@Nullable S part, @Nonnull PartsAccess.Mutable world, @Nullable World theWorld, @Nullable EntityPlayer breaker){
		AppEngME.INSTANCE.getGlobalNBDManager().processDestroyedDevice(part.networkCounterpart);
	}

	protected abstract static class PartDeviceState<P extends PartDevice<P, S, N>, S extends PartDevice.PartDeviceState<P, S, N>, N extends NetDeviceBase<N, S>> extends StateBase<P, S> implements PhysicalDevice<N, S> {

		public PartDeviceState(P part){
			super(part);
		}

		/*
		 * Network counterpart
		 */

		protected N networkCounterpart;

		@Override
		public N getNetworkCounterpart(){
			return networkCounterpart;
		}

		protected abstract N createNewNetworkCounterpart();

		@Override
		public VoxelPosition getPosition(){
			return getAssignedPosRot().getPosition();
		}

		/*
		 * Connections
		 */

		protected ConnectUUID connectUUID = new ConnectUUID();

		@Override
		public ConnectUUID getUUIDForConnection(){
			return connectUUID;
		}

		/*
		 * IO
		 */

		@Override
		public NBTTagCompound serializeNBT(){
			NBTTagCompound nbt = new NBTTagCompound();
			nbt.setTag("duuid", networkCounterpart.getUUID().serializeNBT());
			networkCounterpart.getNetBlock().ifPresent(netBlock -> {
				nbt.setTag("buuid", netBlock.getUUID().serializeNBT());
				netBlock.getNetwork().ifPresent(network -> nbt.setTag("nuuid", network.getUUID().serializeNBT()));
			});

			nbt.setTag("cuuid", connectUUID.serializeNBT());
			return nbt;
		}

		@Override
		public void deserializeNBT(NBTTagCompound nbt){
			DeviceUUID duuid = DeviceUUID.fromNBT(nbt.getCompoundTag("duuid"));
			Optional<NetBlockUUID> buuidO = Optional.ofNullable(nbt.hasKey("buuid") ? nbt.getCompoundTag("buuid") : null).map(NetBlockUUID::fromNBT);
			Optional<NetworkUUID> nuuidO = Optional.ofNullable(nbt.hasKey("nuuid") ? nbt.getCompoundTag("nuuid") : null).map(NetworkUUID::fromNBT);
			this.networkCounterpart = AppEngME.INSTANCE.getGlobalNBDManager().locateOrCreateNetworkCounterpart(Optional.of(duuid), buuidO, nuuidO, this::createNewNetworkCounterpart);

			this.connectUUID = ConnectUUID.fromNBT(nbt.getCompoundTag("cuuid"));
		}

		@Override
		public NBTTagCompound serializeSyncNBT(){
			return new NBTTagCompound();
		}

		@Override
		public void deserializeSyncNBT(NBTTagCompound nbt){

		}

	}

}

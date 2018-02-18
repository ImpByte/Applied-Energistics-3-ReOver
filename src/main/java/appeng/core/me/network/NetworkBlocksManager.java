package appeng.core.me.network;

import appeng.core.me.AppEngME;
import appeng.core.me.api.network.NetBlock;
import appeng.core.me.api.network.NetBlockUUID;
import appeng.core.me.api.network.Network;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.INBTSerializable;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class NetworkBlocksManager implements INBTSerializable<NBTTagCompound> {

	protected final Network network;
	protected Map<NetBlockUUID, NetBlockImpl> netBlocks = new HashMap<>();

	public NetworkBlocksManager(Network network){
		this.network = network;
	}

	@Nullable
	public NetBlock getBlock(NetBlockUUID uuid){
		return netBlocks.get(uuid);
	}

	@Nonnull
	public Collection<NetBlockImpl> getBlocks(){
		return netBlocks.values();
	}

	/*
	 * IO
	 */

	@Override
	public NBTTagCompound serializeNBT(){
		NBTTagCompound nbt = new NBTTagCompound();
		NBTTagList blocks = new NBTTagList();
		this.netBlocks.values().stream().map(AppEngME.INSTANCE.getNBDIO()::serializeNetBlockWithArgs).forEach(blocks::appendTag);
		nbt.setTag("blocks", blocks);
		return nbt;
	}

	@Override
	public void deserializeNBT(NBTTagCompound nbt){
		this.netBlocks.clear();
		NBTTagList blocks = (NBTTagList) nbt.getTag("blocks");
		blocks.forEach(next -> {
			Pair<NetBlockUUID, NetBlockImpl> uuidBlock = AppEngME.INSTANCE.getNBDIO().deserializeNetBlockWithArgs(network, (NBTTagCompound) next);
			this.netBlocks.put(uuidBlock.getLeft(), uuidBlock.getRight());
		});
	}

}
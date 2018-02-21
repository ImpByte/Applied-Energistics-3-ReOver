package appeng.core.me.network.connect;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;

public class DataConnection extends AConnection<DataConnection.Params, NBTTagCompound> {

	public DataConnection(ResourceLocation id){
		super(id);
	}

	@Nonnull
	@Override
	public Params join(@Nonnull Params param1, @Nonnull Params param2){
		return new Params(Math.min(param1.channels, param2.channels), Math.min(param1.dpc, param2.dpc));
	}

	@Nonnull
	@Override
	public Params add(@Nonnull Params param1, @Nonnull Params param2){
		return new Params(Math.max(param1.channels, param2.channels), Math.min(param1.dpc, param2.dpc));
	}

	@Nonnull
	@Override
	public Params subtract(@Nonnull Params param, @Nonnull Params sub){
		return new Params(param.channels - sub.channels, param.dpc);
	}

	@Nonnull
	@Override
	public Params divide(@Nonnull Params param, int parts){
		return new Params(Math.floorDiv(param.channels, parts) + 1, param.dpc);
	}

	@Override
	public NBTTagCompound serializeParam(Params param){
		NBTTagCompound nbt = new NBTTagCompound();
		nbt.setInteger("channels", param.channels);
		nbt.setInteger("dpc", param.dpc);
		return nbt;
	}

	@Override
	public Params deserializeParam(NBTTagCompound nbt){
		return new Params(nbt.getInteger("channels"), nbt.getInteger("dpc"));
	}

	public static class Params implements Comparable<Params> {

		private final int channels, dpc;

		public Params(int channels, int dpc){
			this.channels = channels;
			this.dpc = dpc;
		}

		public int getChannels(){
			return channels;
		}

		public int getDPC(){
			return dpc;
		}

		@Override
		public int compareTo(Params o){
			return channels - o.channels == 0 ? dpc - o.dpc : channels - o.channels;
		}

	}

}
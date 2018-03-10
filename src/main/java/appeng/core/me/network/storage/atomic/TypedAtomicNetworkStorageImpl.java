package appeng.core.me.network.storage.atomic;

import appeng.core.me.api.network.storage.atomic.TypedAtomicNetworkStorage;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.apache.commons.lang3.mutable.MutableInt;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class TypedAtomicNetworkStorageImpl<T> implements TypedAtomicNetworkStorage<T> {

	public TypedAtomicNetworkStorageImpl(Function<T, NBTTagCompound> serializer, Function<NBTTagCompound, T> deserializer){
		this.serializer = serializer;
		this.deserializer = deserializer;
	}

	/*
	 * Storage
	 */

	protected AtomicInteger totalStored = new AtomicInteger();
	protected ConcurrentMap<T, AtomicInteger> storage = new ConcurrentHashMap<>();

	@Override
	public int getTotalStored(){
		return totalStored.get();
	}

	@Override
	public int getStoredTypesCount(){
		return storage.size();
	}

	@Override
	public int getStoredAmount(T t){
		return storage.getOrDefault(t, new AtomicInteger()).get();
	}

	/**
	 * The maximum amount of object that can be stored/extracted.<br>
	 * <b>Must be atomic and side effect free.</b>
	 *
	 * @param t             object
	 * @param currentAmount currently stored amount
	 * @param store         true if store, false if extract
	 * @return maximum amount that can be stored/extracted, unsigned (positive) in both cases
	 */
	protected int getMaxStore(T t, int currentAmount, boolean store){
		return store ? Integer.MAX_VALUE : currentAmount;
	}

	@Override
	public int store(T t, int minAmount, int maxAmount){
		assert minAmount != 0 && maxAmount != 0 && Math.signum(minAmount) == Math.signum(maxAmount);
		boolean store = Math.signum(maxAmount) == 1;
		int min = Math.min(Math.abs(minAmount), Math.abs(maxAmount));
		int max = Math.max(Math.abs(minAmount), Math.abs(maxAmount));
		MutableInt sres = new MutableInt(); //Same invocation, same thread
		(store ? storage.putIfAbsent(t, new AtomicInteger()) : storage.getOrDefault(t, new AtomicInteger())).updateAndGet(amount -> {
			int stored;
			int can = getMaxStore(t, amount, store);
			if(can < min){
				stored = 0;
			} else if(can > max){
				stored = max;
				amount += store ? max : -max;
			} else {
				amount += store ? can : -can;
				stored = can;
			}
			sres.setValue(stored);
			return amount;
		});
		int res = store ? sres.getValue() : -sres.getValue();
		totalStored.addAndGet(res);
		return res;
	}

	/*
	 * IO
	 */

	protected final Function<T, NBTTagCompound> serializer;
	protected final Function<NBTTagCompound, T> deserializer;

	protected NBTTagCompound serializeT(T t){
		return serializer.apply(t);
	}

	protected T deserializeT(NBTTagCompound nbt){
		return deserializer.apply(nbt);
	}

	@Override
	public NBTTagCompound serializeNBT(){
		NBTTagCompound nbt = new NBTTagCompound();
		NBTTagList storage = new NBTTagList();
		this.storage.forEach((t, amount) -> {
			if(amount.get() != 0){
				NBTTagCompound next = new NBTTagCompound();
				next.setTag("object", serializeT(t));
				next.setInteger("amount", amount.get());
				storage.appendTag(next);
			}
		});
		nbt.setTag("storage", storage);
		return nbt;
	}

	@Override
	public void deserializeNBT(NBTTagCompound nbt){
		this.storage.clear();
		NBTTagList storage = (NBTTagList) nbt.getTag("storage");
		storage.forEach(nbtBase -> {
			NBTTagCompound next = (NBTTagCompound) nbtBase;
			this.storage.put(deserializeT(next.getCompoundTag("object")), new AtomicInteger(next.getInteger("amount")));
		});
	}

}
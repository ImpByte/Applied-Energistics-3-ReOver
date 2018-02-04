package appeng.core.me.bootstrap;

import appeng.api.bootstrap.DefinitionFactory;
import appeng.core.lib.bootstrap.DefinitionBuilder;
import appeng.core.me.api.bootstrap.IDeviceBuilder;
import appeng.core.me.api.definition.IDeviceDefinition;
import appeng.core.me.api.network.NetDevice;
import appeng.core.me.api.network.PhysicalDevice;
import appeng.core.me.api.network.device.DeviceLoader;
import appeng.core.me.api.network.device.DeviceRegistryEntry;
import appeng.core.me.definition.DeviceDefinition;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;

public class DeviceDefinitionBuilder<N extends NetDevice<N, P>, P extends PhysicalDevice<N, P>> extends DefinitionBuilder<DeviceRegistryEntryImpl<N, P>, DeviceRegistryEntry<N, P>, IDeviceDefinition<N, P>, DeviceDefinitionBuilder<N, P>> implements IDeviceBuilder<N, P, DeviceDefinitionBuilder<N, P>> {

	private DeviceLoader<N, P> deserializer;

	public DeviceDefinitionBuilder(DefinitionFactory factory, ResourceLocation registryName){
		super(factory, registryName, new DeviceRegistryEntryImpl<>(null), "device");
	}

	@Override
	public DeviceDefinitionBuilder<N, P> deserializer(DeviceLoader<N, P> deserializer){
		this.deserializer = deserializer;
		return this;
	}

	@Override
	protected IDeviceDefinition<N, P> def(@Nullable DeviceRegistryEntryImpl<N, P> reg){
		reg.deserializer = this.deserializer;
		return new DeviceDefinition<>(registryName, reg);
	}
}

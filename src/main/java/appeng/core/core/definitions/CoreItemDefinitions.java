package appeng.core.core.definitions;

import appeng.api.definitions.IItemDefinition;
import appeng.core.AppEng;
import appeng.core.api.definitions.ICoreItemDefinitions;
import appeng.core.item.ItemMaterial;
import appeng.core.lib.definitions.Definitions;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;

public class CoreItemDefinitions extends Definitions<Item, IItemDefinition<Item>> implements ICoreItemDefinitions {

	private final IItemDefinition material;

	public CoreItemDefinitions(FeatureFactory registry){
		this.material = registry.item(new ResourceLocation(AppEng.MODID, "material"), new ItemMaterial()).build();

		init(registry.buildDefaultItemBlocks());
	}

}

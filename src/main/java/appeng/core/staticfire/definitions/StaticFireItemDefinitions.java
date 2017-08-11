package appeng.core.staticfire.definitions;

import appeng.api.bootstrap.DefinitionFactory;
import appeng.core.AppEng;
import appeng.core.core.api.definition.IItemDefinition;
import appeng.core.core.bootstrap.ItemDefinitionBuilder;
import appeng.core.lib.definitions.Definitions;
import appeng.core.staticfire.api.definitions.IStaticFireItemDefinitions;
import appeng.core.staticfire.item.CarbonCopy;
import appeng.core.staticfire.item.ItemTest;
import appeng.core.staticfire.block.QuantumPillarBase;
import appeng.core.staticfire.item.SkyStoneIngot;
import appeng.core.staticfire.item.StaticFireItemBase;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;

public class StaticFireItemDefinitions extends  Definitions<Item, IItemDefinition<Item>> implements IStaticFireItemDefinitions {

    public StaticFireItemDefinitions(DefinitionFactory registry){

        //registry.definitionBuilder(new ResourceLocation(AppEng.MODID, "test"), ih(new ItemBlock(new TestBlock()))).build();
        //Reg(registry, new QuantumPillarBase());
        Reg(registry, new ItemTest());
        Reg(registry, new CarbonCopy());
        Reg(registry, new SkyStoneIngot());
        //init(registry);
        System.out.println("Stuff");
    }

    private DefinitionFactory.InputHandler<Item, Item> ih(Item item){
        return new DefinitionFactory.InputHandler<Item, Item>(item) {};
    }

    public <T extends Item & StaticFireItemBase> void Reg(DefinitionFactory registry, T item)
    {
        ItemDefinitionBuilder a = registry.definitionBuilder(new ResourceLocation(AppEng.MODID, item.getRegistryNameSF()), ih(item));
        a.defaultModel();
        a.build();
    }
}
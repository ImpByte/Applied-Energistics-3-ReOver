package appeng.core.me.definitions;

import appeng.api.bootstrap.DefinitionFactory;
import appeng.core.AppEng;
import appeng.core.lib.definitions.Definitions;
import appeng.core.me.api.bootstrap.IPartBuilder;
import appeng.core.me.api.definition.IPartDefinition;
import appeng.core.me.api.definitions.IMEPartDefinitions;
import appeng.core.me.api.parts.part.Part;
import appeng.core.me.parts.part.PartDummy;
import appeng.core.me.parts.placement.SideIsBottomPlacementLogic;
import net.minecraft.util.ResourceLocation;

public class MEPartDefinitions<P extends Part<P, S>, S extends Part.State<P, S>> extends Definitions<P, IPartDefinition<P, S>> implements IMEPartDefinitions<P, S> {

	private final IPartDefinition<P, S> glassCableMicroGreen;
	private final IPartDefinition<P, S> glassCableNormalGreen;

	public MEPartDefinitions(DefinitionFactory registry){
		glassCableMicroGreen = registry.<P, IPartDefinition<P, S>, IPartBuilder<P, S, ?>, P>definitionBuilder(new ResourceLocation(AppEng.MODID, "glass_cable_micro_green"), ih(new PartDummy(false))).mesh(new ResourceLocation(AppEng.MODID, "cable/glass/micro/green.obj")).createDefaultPlacerItem().build();
		glassCableNormalGreen = registry.<P, IPartDefinition<P, S>, IPartBuilder<P, S, ?>, P>definitionBuilder(new ResourceLocation(AppEng.MODID, "glass_cable_normal_green"), ih(new PartDummy(true))).mesh(new ResourceLocation(AppEng.MODID, "cable/glass/normal/green.obj")).createDefaultPlacerItem().build();
		registry.<P, IPartDefinition<P, S>, IPartBuilder<P, S, ?>, P>definitionBuilder(new ResourceLocation(AppEng.MODID, "unlocked_block"), ih(new PartDummy(false))).createDefaultPlacerItem().build();
		registry.<P, IPartDefinition<P, S>, IPartBuilder<P, S, ?>, P>definitionBuilder(new ResourceLocation(AppEng.MODID, "rotation_test"), ih(new PartDummy(true))).createDefaultPlacerItem().build();
		registry.<P, IPartDefinition<P, S>, IPartBuilder<P, S, ?>, P>definitionBuilder(new ResourceLocation(AppEng.MODID, "tower"), ih(new PartDummy(true))).createDefaultPlacerItem(part -> new SideIsBottomPlacementLogic<>(part.maybe().get())).build();
		registry.<P, IPartDefinition<P, S>, IPartBuilder<P, S, ?>, P>definitionBuilder(new ResourceLocation(AppEng.MODID, "dish"), ih(new PartDummy(true))).createDefaultPlacerItem(part -> new SideIsBottomPlacementLogic<>(part.maybe().get())).build();
	}

	private DefinitionFactory.InputHandler<Part, Part> ih(Part part){
		return new DefinitionFactory.InputHandler<Part, Part>(part) {};
	}

}

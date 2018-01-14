package appeng.core.me.api.bootstrap;

import appeng.api.bootstrap.IDefinitionBuilder;
import appeng.core.me.api.client.part.PartRenderingHandler;
import appeng.core.me.api.definition.IPartDefinition;
import appeng.core.me.api.parts.part.Part;
import appeng.core.me.api.parts.placement.PartPlacementLogic;
import net.minecraft.util.ResourceLocation;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

public interface IPartBuilder<P extends Part<P, S>, S extends Part.State<P, S>, PP extends IPartBuilder<P, S, PP>> extends IDefinitionBuilder<P, IPartDefinition<P, S>, PP> {

	PP createDefaultPlacerItem(Function<IPartDefinition<P, S>, PartPlacementLogic> placementLogic);

	PP createDefaultPlacerItem();

	PP rootMesh(ResourceLocation model);

	PP overrideRenderingHandler(Supplier<Optional<PartRenderingHandler<P, S>>> renderingHandler);

}
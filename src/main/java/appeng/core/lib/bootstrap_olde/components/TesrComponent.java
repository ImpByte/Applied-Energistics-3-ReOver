package appeng.core.lib.bootstrap_olde.components;

import appeng.api.bootstrap.InitializationComponent;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Registers a TESR for a given tile entity class.
 *
 * @param <T>
 */
public class TesrComponent<T extends TileEntity> implements InitializationComponent.PreInit {

	private final Class<T> tileEntityClass;

	private final TileEntitySpecialRenderer<? super T> tesr;

	public TesrComponent(Class<T> tileEntityClass, TileEntitySpecialRenderer<? super T> tesr){
		this.tileEntityClass = tileEntityClass;
		this.tesr = tesr;
	}

	@Override
	public void preInit(Side side){
		ClientRegistry.bindTileEntitySpecialRenderer(tileEntityClass, tesr);
	}
}

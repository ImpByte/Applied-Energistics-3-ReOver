package appeng.core.me.parts.part;

import appeng.api.bootstrap.InitializationComponent;
import appeng.core.AppEng;
import appeng.core.lib.client.resource.ClientResourceHelper;
import appeng.core.lib.raytrace.RayTraceHelper;
import appeng.core.lib.resource.ResourceLocationHelper;
import appeng.core.me.AppEngME;
import appeng.core.me.api.parts.PartPositionRotation;
import appeng.core.me.api.parts.VoxelPosition;
import appeng.core.me.api.parts.VoxelPositionSide;
import appeng.core.me.api.parts.container.IPartsContainer;
import appeng.core.me.api.parts.container.PartsAccess;
import appeng.core.me.api.parts.part.Part;
import appeng.core.me.api.parts.part.PartsHelper;
import appeng.core.me.api.parts.placement.PartPlacementLogic;
import appeng.core.me.client.part.container.ClientWorldPartsAccess;
import appeng.core.me.parts.container.WorldPartsAccess;
import appeng.core.me.parts.placement.DefaultPartPlacementLogic;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.owens.oobjloader.builder.Mesh;
import com.owens.oobjloader.builder.VertexGeometric;
import com.owens.oobjloader.parser.OObjMeshLoader;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.commons.lang3.mutable.MutableObject;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.math3.geometry.euclidean.threed.Plane;
import org.apache.commons.math3.geometry.euclidean.threed.Vector3D;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static appeng.core.me.api.parts.container.GlobalVoxelsInfo.*;

public class PartsHelperImpl implements PartsHelper, InitializationComponent {

	public static final Logger logger = LogManager.getLogger("Parts Helper");

	private PartGroupsHelper groupsHelper = new PartGroupsHelper(this);

	/*
	 * Init
	 */

	@Override
	public void preInit(){
		MinecraftForge.EVENT_BUS.register(this);
		AppEngME.proxy.client().acceptPreInit(() -> ClientResourceHelper.registerReloadListener(resourceManager -> loadMeshes()));

		groupsHelper.preInit();
	}

	@Override
	public void init(){
		registerCustomPartDataLoader(PLAYERINTERFACELOADER, (part, meshLoader, voxelizer, rootMeshVoxels) -> meshLoader.apply("pi").map(mesh -> {
			Map<VoxelPositionSide, String> pis = new HashMap<>();
			mesh.groups.keySet().forEach(g -> forEachInterface(voxelizer.apply(mesh, Predicate.isEqual(g)), rootMeshVoxels, vps -> pis.put(vps, g)));
			return new PlayerInterfaces(pis);
		}));
	}

	protected void forEachInterface(Set<VoxelPosition> inter2faces, Set<VoxelPosition> in, Consumer<VoxelPositionSide> interfaceConsumer){
		inter2faces.stream().filter(in::contains).forEach(inVoxel -> {
			for(EnumFacing side : EnumFacing.values()){
				VoxelPosition outVoxel = inVoxel.offsetLocal(side);
				if(inter2faces.contains(outVoxel) && !in.contains(outVoxel)) interfaceConsumer.accept(new VoxelPositionSide(inVoxel, side));
			}
		});
	}

	@Override
	public void postInit(){
		loadMeshes();
		groupsHelper.loadGroups();
	}

	/*
	 * Impl
	 */

	protected List<Pair<ResourceLocation, CustomPartDataLoader<?>>> cpdl = new ArrayList<>();

	@Override
	public <T> void registerCustomPartDataLoader(ResourceLocation id, CustomPartDataLoader<T> loader){
		cpdl.add(new ImmutablePair<>(id, loader));
	}

	@Override
	public <T> Optional<T> getCustomPartData(Part part, ResourceLocation id){
		return Optional.ofNullable((T) getData(part).cpd.get(id));
	}

	@Override
	public PartPlacementLogic createDefaultPlacementLogic(Part part){
		return new DefaultPartPlacementLogic(part);
	}

	/*
	 * Caps
	 */

	@CapabilityInject(IPartsContainer.class)
	public static Capability<IPartsContainer> partsContainerCapability;

	@CapabilityInject(PartsAccess.Mutable.class)
	public static Capability<WorldPartsAccess> worldPartsAccessCapability;

	@SubscribeEvent
	public void attachWorldCaps(AttachCapabilitiesEvent<World> event){
		event.addCapability(new ResourceLocation(AppEng.MODID, "global_parts_pool"), new ICapabilityProvider() {

			WorldPartsAccess pool = !event.getObject().isRemote ? new WorldPartsAccess(event.getObject()) : new ClientWorldPartsAccess(event.getObject());

			@Override
			public boolean hasCapability(@Nonnull Capability<?> capability, @Nullable EnumFacing facing){
				return capability == worldPartsAccessCapability;
			}

			@Nullable
			@Override
			public <T> T getCapability(@Nonnull Capability<T> capability, @Nullable EnumFacing facing){
				return capability == worldPartsAccessCapability ? (T) pool : null;
			}

		});
	}

	/*
	 * Interaction
	 */

	@SubscribeEvent
	public void tryBreakBlock(BlockEvent.BreakEvent event){
		Optional.ofNullable(event.getWorld().getTileEntity(event.getPos())).map(tile -> tile.getCapability(partsContainerCapability, null)).ifPresent(container -> {
			RayTraceResult rayTrace = RayTraceHelper.rayTrace(event.getPlayer());
			PartsAccess.Mutable worldPartsAccess = event.getPlayer().world.getCapability(PartsHelperImpl.worldPartsAccessCapability, null);
			if(!event.getWorld().isRemote && rayTrace.hitInfo instanceof VoxelPosition && ((VoxelPosition) rayTrace.hitInfo).getGlobalPosition().equals(container.getGlobalPosition()))
				worldPartsAccess.removePart((VoxelPosition) rayTrace.hitInfo).ifPresent(uuidPart -> uuidPart.getRight().getPart().onBroken(uuidPart.getRight().getState().orElse(null), worldPartsAccess, event.getWorld(), event.getPlayer()));
			event.setCanceled(true);
		});
	}

	/*
	 * Player Interface
	 */

	@Nonnull
	@Override
	public Optional<String> getPlayerInterface(@Nonnull Part.State part, @Nonnull VoxelPositionSide voxelPositionSide){
		return getPlayerInterfaces(part.getPart()).map(pi -> pi.pis.get(part.getAssignedPosRot().untransform(voxelPositionSide)));
	}

	protected static final ResourceLocation PLAYERINTERFACELOADER = new ResourceLocation(AppEng.MODID, "pi");

	protected Optional<PlayerInterfaces> getPlayerInterfaces(Part part){
		return getCustomPartData(part, PLAYERINTERFACELOADER);
	}

	protected static class PlayerInterfaces {

		protected final Map<VoxelPositionSide, String> pis;

		public PlayerInterfaces(Map<VoxelPositionSide, String> pis){
			this.pis = pis;
		}

	}

	/*
	 * Meshing
	 */

	public static ResourceLocation getFullRootMeshLocation(Part part, String suffix){
		String path = part.getRootMesh().getPath();
		if(suffix != null) path = path.substring(0, path.length() - 3) + suffix + ".obj";
		return new ResourceLocation(part.getRootMesh().getNamespace(), "models/part/" + path);
	}

	public static ResourceLocation getFullStateMeshLocation(ResourceLocation mesh){
		return new ResourceLocation(mesh.getNamespace(), "part/" + mesh.getPath());
	}

	private Map<ResourceLocation, PartData> partDataMap = new HashMap<>();

	private Voxelizer voxelizer = new Voxelizer(Voxelizer.Voxelization.S6);

	private void loadMeshes(){
		logger.info("Reloading meshes");
		long time = System.currentTimeMillis();
		partDataMap.clear();
		for(Part part : AppEngME.INSTANCE.getPartRegistry())
			partDataMap.put(part.getRegistryName(), new PartData(part, voxelizer));
		logger.info("Reloaded meshes in " + (System.currentTimeMillis() - time));
	}

	static Optional<Mesh> loadMesh(Part part, String suffix){
		//TODO 1.12-MIPA We only need geometry
		Mesh mesh = new Mesh();
		try{
			new OObjMeshLoader<>(mesh, AppEngME.proxy.getResourceHelper(), getFullRootMeshLocation(part, suffix));
		} catch(IOException e){
			logger.error("Failed to load mesh for " + part.getRegistryName(), e);
		}
		return mesh.verticesG.isEmpty() ? Optional.empty() : Optional.of(mesh);
	}

	protected PartData getData(Part part){
		return partDataMap.get(part.getRegistryName());
	}

	public boolean supportsRotation(Part part){
		return part.supportsRotation() && getData(part).supportsRotation;
	}

	public AxisAlignedBB getPartVoxelBB(Part part){
		return getData(part).vbbox;
	}

	public Stream<VoxelPosition> getVoxels(Part part, PartPositionRotation positionRotation){
		return getData(part).voxels.stream().map(positionRotation.getRotation()::rotate).map(positionRotation.getRotationCenterPosition()::add);
	}

	public Stream<BlockPos> getVoxels(BlockPos gHandlerPos, Part part, PartPositionRotation positionRotation){
		return getVoxels(part, positionRotation).filter(voxel -> gHandlerPos.equals(voxel.getGlobalPosition())).map(VoxelPosition::getLocalPosition);
	}

	public NBTTagCompound serialize(Part.State part){
		NBTTagCompound nbt = new NBTTagCompound();
		nbt.setTag("id", ResourceLocationHelper.serialize(part.getPart().getRegistryName()));
		NBTBase state = part.serializeNBT();
		nbt.setTag("state", state != null ? state : new NBTTagCompound());
		return nbt;
	}

	public <P extends Part<P, S>, S extends Part.State<P, S>> S deserialize(NBTTagCompound nbt){
		S state = (S) AppEngME.INSTANCE.getPartRegistry().getValue(ResourceLocationHelper.deserialize(nbt.getCompoundTag("id"))).createNewState();
		state.deserializeNBT(nbt.getCompoundTag("state"));
		return state;
	}

	public AxisAlignedBB applyTransforms(AxisAlignedBB box, PartPositionRotation positionRotation){
		return positionRotation.getRotationCenterPosition().translate(positionRotation.getRotation().rotate(box));
	}

	public AxisAlignedBB getGlobalBBox(Part part, PartPositionRotation positionRotation){
		return applyTransforms(getData(part).gbbox, positionRotation);
	}

	public AxisAlignedBB getLocalBBox(Part part, PartPositionRotation positionRotation){
		return applyTransforms(getData(part).vbbox, positionRotation);
	}

	class PartData {

		private AxisAlignedBB toBox(VertexGeometric vertex){
			return new AxisAlignedBB(vertex.x, vertex.y, vertex.z, vertex.x, vertex.y, vertex.z);
		}

		final Mesh mesh;
		final AxisAlignedBB vbbox;
		final AxisAlignedBB gbbox;
		final boolean supportsRotation;
		final ImmutableSet<VoxelPosition> voxels;
		final ImmutableMap<ResourceLocation, Object> cpd;

		public PartData(Part part, Voxelizer voxelizer){
			logger.info("Reloading " + part.getRegistryName());
			long time = System.currentTimeMillis();
			mesh = loadMesh(part, null).orElseGet(Mesh::new);
			if(!mesh.verticesG.isEmpty()){
				VertexGeometric first = mesh.verticesG.get(0);
				MutableObject<AxisAlignedBB> bbox = new MutableObject<>(toBox(first));
				mesh.verticesG.forEach(vertex -> bbox.setValue(bbox.getValue().union(toBox(vertex))));
				this.vbbox = new AxisAlignedBB(Math.floor(bbox.getValue().minX * VOXELSPERBLOCKAXISD), Math.floor(bbox.getValue().minY * VOXELSPERBLOCKAXISD), Math.floor(bbox.getValue().minZ * VOXELSPERBLOCKAXISD), Math.ceil(bbox.getValue().maxX * VOXELSPERBLOCKAXISD), Math.ceil(bbox.getValue().maxY * VOXELSPERBLOCKAXISD), Math.ceil(bbox.getValue().maxZ * VOXELSPERBLOCKAXISD));
			} else this.vbbox = new AxisAlignedBB(BlockPos.ORIGIN);
			this.gbbox = new AxisAlignedBB(vbbox.minX * VOXELSIZED, vbbox.minY * VOXELSIZED, vbbox.minZ * VOXELSIZED, vbbox.maxX * VOXELSIZED, vbbox.maxY * VOXELSIZED, vbbox.maxZ * VOXELSIZED);
			supportsRotation = (vbbox.minX <= -1 && vbbox.maxX >= 1) || (vbbox.minY <= 1 && vbbox.maxY >= 1) || (vbbox.minZ <= 1 && vbbox.maxZ >= 1);
			voxels = voxelizer.voxelize(mesh, null);
			ImmutableMap.Builder<ResourceLocation, Object> cpdb = new ImmutableMap.Builder<>();
			cpdl.forEach(idLoader -> idLoader.getRight().load(part, suffix -> loadMesh(part, suffix), voxelizer::voxelize, voxels).ifPresent(cd -> cpdb.put(idLoader.getLeft(), cd)));
			cpd = cpdb.build();
			logger.info("Reloaded " + part.getRegistryName() + " in " + (System.currentTimeMillis() - time));
		}

	}

	static class Voxelizer {

		static final double L = VOXELSIZED;
		static final double K = L * 0.86602540378;

		static Stream<Edge> edges(Mesh mesh, Stream<com.owens.oobjloader.builder.Face> facesStream){
			MutableObject<Stream<Edge>> stream = new MutableObject<>(Stream.empty());
			facesStream.forEach(face -> {
				ArrayList<Edge> edges = new ArrayList<>();
				for(int i = 0; i < face.vertices.size(); i++){
					VertexGeometric v1 = face.vertices.get(i).v;
					VertexGeometric v2 = face.vertices.get((i + 1) % face.vertices.size()).v;
					edges.add(new Edge(new Vector3d(v1.x, v1.y, v1.z), new Vector3d(v2.x, v2.y, v2.z)));
				}
				stream.setValue(Stream.concat(stream.getValue(), edges.stream()));
			});
			return stream.getValue();
		}

		Voxelization mode;

		public Voxelizer(Voxelization mode){
			this.mode = mode;
		}

		ImmutableSet<VoxelPosition> voxelize(Mesh mesh, @Nullable Predicate<String> groupFilter){
			if(!mesh.voxPreProcessed) mesh.faceVerticeList.forEach(faceVertex -> {
				faceVertex.v.x -= faceVertex.n.x * VOXELSIZEF4;
				faceVertex.v.y -= faceVertex.n.y * VOXELSIZEF4;
				faceVertex.v.z -= faceVertex.n.z * VOXELSIZEF4;
			});
			mesh.voxPreProcessed = true;
			Set<com.owens.oobjloader.builder.Face> groupFaces = groupFilter == null ? null : mesh.groups.entrySet().stream().filter(e -> groupFilter.test(e.getKey())).flatMap(e -> e.getValue().stream()).collect(Collectors.toSet());
			Supplier<Stream<com.owens.oobjloader.builder.Face>> facesStream = () -> mesh.faces.stream().filter(groupFilter == null ? f -> true : groupFaces::contains);

			ImmutableSet.Builder<VoxelPosition> builder = ImmutableSet.builder();

			long time = System.currentTimeMillis();
			facesStream.get().flatMap(face -> face.vertices.stream().map(fv -> fv.v)).forEach(vertexGeometric -> VoxelPosition.allVoxelsIn(new AxisAlignedBB(vertexGeometric.x - mode.Rc, vertexGeometric.y - mode.Rc, vertexGeometric.z - mode.Rc, vertexGeometric.x + mode.Rc, vertexGeometric.y + mode.Rc, vertexGeometric.z + mode.Rc)).filter(voxel -> /*bbox.contains(voxel.globalCenterVec()) &&*/ voxel.globalCenterVec().squareDistanceTo(vertexGeometric.x, vertexGeometric.y, vertexGeometric.z) <= mode.Rc * mode.Rc).forEach(builder::add));
			logger.info("Voxelized vertices in " + (System.currentTimeMillis() - time));

			time = System.currentTimeMillis();
			edges(mesh, facesStream.get()).forEach(edge -> VoxelPosition.allVoxelsIn(edge.getBBox(mode)).filter(voxel -> /*bbox.contains(voxel.globalCenterVec()) &&*/ edge.distanceTo(voxel.globalCenterVector()) <= mode.Rc).forEach(builder::add));
			logger.info("Voxelized edges in " + (System.currentTimeMillis() - time));

			time = System.currentTimeMillis();
			facesStream.get().forEach(face -> {
				if(face.vertices.size() == 3){
					VertexGeometric v1 = face.vertices.get(0).v;
					VertexGeometric v2 = face.vertices.get(1).v;
					VertexGeometric v3 = face.vertices.get(2).v;
					new Face(new Vector3d(v1.x, v1.y, v1.z), new Vector3d(v2.x, v2.y, v2.z), new Vector3d(v3.x, v3.y, v3.z)).getAllVoxels(mode).forEach(builder::add);
					new Face(new Vector3d(v2.x, v2.y, v2.z), new Vector3d(v1.x, v1.y, v1.z), new Vector3d(v3.x, v3.y, v3.z)).getAllVoxels(mode).forEach(builder::add);
				} else if(face.vertices.size() == 4){
					//FIXME Quads aren't working...
					VertexGeometric v1 = face.vertices.get(0).v;
					VertexGeometric v2 = face.vertices.get(1).v;
					VertexGeometric v3 = face.vertices.get(2).v;
					VertexGeometric v4 = face.vertices.get(3).v;
					new Face(new Vector3d(v1.x, v1.y, v1.z), new Vector3d(v2.x, v2.y, v2.z), new Vector3d(v4.x, v4.y, v4.z)).getAllVoxels(mode).forEach(builder::add);
					new Face(new Vector3d(v4.x, v4.y, v4.z), new Vector3d(v2.x, v2.y, v2.z), new Vector3d(v3.x, v3.y, v3.z)).getAllVoxels(mode).forEach(builder::add);
				}
			});
			logger.info("Voxelized faces in " + (System.currentTimeMillis() - time));

			return builder.build();
		}

		static class Edge {

			Vector3dc v1;
			Vector3dc v2;

			public Edge(Vector3d v1, Vector3d v2){
				this.v1 = v1;
				this.v2 = v2;
			}

			double distanceTo(Vector3dc v0){
				return v2.sub(v1, new Vector3d()).angle(v0.sub(v1, new Vector3d())) > Math.PI / 2 ? v1.distance(v0) : v1.sub(v2, new Vector3d()).angle(v0.sub(v2, new Vector3d())) > Math.PI / 2 ? v2.distance(v0) : v0.sub(v1, new Vector3d()).cross(v0.sub(v2, new Vector3d())).length() / v2.sub(v1, new Vector3d()).length();
			}

			AxisAlignedBB getBBox(Voxelization voxelization){
				return new AxisAlignedBB(v1.x(), v1.y(), v1.z(), v2.z(), v2.y(), v2.z()).grow(voxelization.Rc);
			}

		}

		static class Face {

			static final double TOLERANCE = 1.0e-8;

			Vector3dc v1;
			Vector3dc v2;
			Vector3dc v3;

			public Face(Vector3dc v1, Vector3dc v2, Vector3dc v3){
				this.v1 = v1;
				this.v2 = v2;
				this.v3 = v3;
			}

			Plane getPlane(){
				return new Plane(new Vector3D(v1.x(), v1.y(), v1.z()), new Vector3D(v2.x(), v2.y(), v2.z()), new Vector3D(v3.x(), v3.y(), v3.z()), TOLERANCE);
			}

			AxisAlignedBB getBBox(Voxelization voxelization){
				return new AxisAlignedBB(v1.x(), v1.y(), v1.z(), v2.z(), v2.y(), v2.z()).union(new AxisAlignedBB(v1.x(), v1.y(), v1.z(), v3.z(), v3.y(), v3.z())).grow(voxelization.Rc);
			}

			Stream<VoxelPosition> getAllVoxels(Voxelization voxelization){
				if(v1.distance(v2) < VOXELSIZED4 || v2.distance(v3) < VOXELSIZED4 || v3.distance(v1) < VOXELSIZED4)
					return Stream.empty();

				Plane plane = getPlane();
				Predicate<VoxelPosition> inThePlane = voxelPosition -> {
					//FIXME Artifacts on Z-

					Vector3D nC2PD = plane.getNormal().scalarMultiply(-1);
					Vector3d nC2Pd = new Vector3d(nC2PD.getX(), nC2PD.getY(), nC2PD.getZ());
					double t;
					if(!voxelization.AOrB){
						double angleMin = 1;
						for(double x = -K; x < 2 * K; x += 2 * K)
							for(double y = -K; y < 2 * K; y += 2 * K)
								for(double z = -K; z < 2 * K; z += 2 * K)
									angleMin = Math.min(angleMin, Math.abs(nC2Pd.angle(new Vector3d(x, y, z))));
						t = K * Math.cos(angleMin);
					} else {
						double angleMin = Math.PI;
						for(EnumFacing facing : EnumFacing.values())
							angleMin = Math.min(angleMin, Math.abs(nC2Pd.angle(new Vector3d(facing.getDirectionVec().getX(), facing.getDirectionVec().getY(), facing.getDirectionVec().getZ()))));
						t = L / 2 * Math.cos(angleMin);
					}

					Vector3d vec = voxelPosition.globalCenterVector();
					double distance = plane.getOffset(new Vector3D(vec.x, vec.y, vec.z));
					return Math.abs(distance) <= t;
				};
				Plane e1 = new Plane(new Vector3D(v1.x(), v1.y(), v1.z()), new Vector3D(v2.x(), v2.y(), v2.z()), new Vector3D(v1.x(), v1.y(), v1.z()).add(plane.getNormal()), TOLERANCE);
				Vector3d v123 = v3.sub(v1, new Vector3d());
				Plane e12 = e1.translate(new Vector3D(v123.x, v123.y, v123.z));
				double d1212 = Math.abs(e1.getOffset(e12));

				Plane e2 = new Plane(new Vector3D(v2.x(), v2.y(), v2.z()), new Vector3D(v3.x(), v3.y(), v3.z()), new Vector3D(v2.x(), v2.y(), v2.z()).add(plane.getNormal()), TOLERANCE);
				Vector3d v221 = v1.sub(v2, new Vector3d());
				Plane e22 = e2.translate(new Vector3D(v221.x, v221.y, v221.z));
				double d2222 = Math.abs(e2.getOffset(e22));

				Plane e3 = new Plane(new Vector3D(v3.x(), v3.y(), v3.z()), new Vector3D(v1.x(), v1.y(), v1.z()), new Vector3D(v3.x(), v3.y(), v3.z()).add(plane.getNormal()), TOLERANCE);
				Vector3d v322 = v2.sub(v3, new Vector3d());
				Plane e32 = e3.translate(new Vector3D(v322.x, v322.y, v322.z));
				double d3232 = Math.abs(e3.getOffset(e32));

				Predicate<VoxelPosition> insideTriangle = voxelPosition -> {
					Vector3d c = voxelPosition.asVector3d();
					Vector3D cD = new Vector3D(c.x, c.y, c.z);
					return Math.abs(e1.getOffset(cD)) <= d1212 && Math.abs(e12.getOffset(cD)) <= d1212 && Math.abs(e3.getOffset(cD)) <= d2222 && Math.abs(e22.getOffset(cD)) <= d2222 && Math.abs(e3.getOffset(cD)) <= d3232 && Math.abs(e32.getOffset(cD)) <= d3232;
				};
				return VoxelPosition.allVoxelsIn(getBBox(voxelization)).filter(inThePlane).filter(insideTriangle);
			}

		}

		enum Voxelization {

			S6(L / 2, true), S26(K, false);

			final double Rc;
			final boolean AOrB;
			//true for beta

			Voxelization(double Rc, boolean AOrB){
				this.Rc = Rc;
				this.AOrB = AOrB;
			}

		}

	}

}

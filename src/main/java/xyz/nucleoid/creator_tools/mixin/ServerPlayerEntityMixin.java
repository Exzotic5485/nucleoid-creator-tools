package xyz.nucleoid.creator_tools.mixin;

import com.mojang.authlib.GameProfile;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.fabricmc.fabric.api.util.NbtType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.nucleoid.creator_tools.CreatorTools;
import xyz.nucleoid.creator_tools.workspace.MapWorkspaceManager;
import xyz.nucleoid.creator_tools.workspace.ReturnPosition;
import xyz.nucleoid.creator_tools.workspace.WorkspaceTraveler;

import java.util.Map;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin extends PlayerEntity implements WorkspaceTraveler {
    @Shadow
    @Final
    public MinecraftServer server;

    private ReturnPosition leaveReturn;
    private final Map<RegistryKey<World>, ReturnPosition> workspaceReturns = new Reference2ObjectOpenHashMap<>();

    private ServerPlayerEntityMixin(World world, BlockPos blockPos, float yaw, GameProfile gameProfile) {
        super(world, blockPos, yaw, gameProfile);
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("RETURN"))
    private void writeData(NbtCompound root, CallbackInfo ci) {
        var creatorTools = new NbtCompound();

        var workspaceReturns = new NbtCompound();

        for (var entry : this.workspaceReturns.entrySet()) {
            var key = entry.getKey().getValue();
            var position = entry.getValue();
            workspaceReturns.put(key.toString(), position.write(new NbtCompound()));
        }

        creatorTools.put("workspace_return", workspaceReturns);

        if (this.leaveReturn != null) {
            creatorTools.put("leave_return", this.leaveReturn.write(new NbtCompound()));
        }

        root.put(CreatorTools.ID, creatorTools);
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("RETURN"))
    private void readData(NbtCompound root, CallbackInfo ci) {
        var creatorTools = root.getCompound(CreatorTools.ID);

        this.workspaceReturns.clear();
        this.leaveReturn = null;

        var workspaceReturnPositions = creatorTools.getCompound("workspace_return");
        for (var key : workspaceReturnPositions.getKeys()) {
            var dimensionKey = RegistryKey.of(Registry.WORLD_KEY, new Identifier(key));
            var position = ReturnPosition.read(workspaceReturnPositions.getCompound(key));
            this.workspaceReturns.put(dimensionKey, position);
        }

        if (creatorTools.contains("leave_return", NbtType.COMPOUND)) {
            this.leaveReturn = ReturnPosition.read(creatorTools.getCompound("leave_return"));
        }
    }

    @Inject(method = "copyFrom", at = @At("RETURN"))
    private void copyFrom(ServerPlayerEntity from, boolean alive, CallbackInfo ci) {
        var fromTraveler = (ServerPlayerEntityMixin) (Object) from;
        this.leaveReturn = fromTraveler.leaveReturn;
        this.workspaceReturns.clear();
        this.workspaceReturns.putAll(fromTraveler.workspaceReturns);
    }

    @Inject(method = "teleport", at = @At("HEAD"))
    private void onTeleport(ServerWorld targetWorld, double x, double y, double z, float yaw, float pitch, CallbackInfo ci) {
        this.onDimensionChange(targetWorld);
    }

    @Inject(method = "moveToWorld", at = @At("HEAD"))
    private void onMoveToWorld(ServerWorld targetWorld, CallbackInfoReturnable<Entity> ci) {
        this.onDimensionChange(targetWorld);
    }

    private void onDimensionChange(ServerWorld targetWorld) {
        var sourceDimension = this.world.getRegistryKey();
        var targetDimension = targetWorld.getRegistryKey();

        var workspaceManager = MapWorkspaceManager.get(this.server);
        if (workspaceManager.isWorkspace(sourceDimension)) {
            this.workspaceReturns.put(sourceDimension, ReturnPosition.capture(this));
        } else if (workspaceManager.isWorkspace(targetDimension)) {
            this.leaveReturn = ReturnPosition.capture(this);
        }
    }

    @Nullable
    @Override
    public ReturnPosition getReturnFor(RegistryKey<World> dimension) {
        return this.workspaceReturns.get(dimension);
    }

    @Nullable
    @Override
    public ReturnPosition getLeaveReturn() {
        return this.leaveReturn;
    }
}

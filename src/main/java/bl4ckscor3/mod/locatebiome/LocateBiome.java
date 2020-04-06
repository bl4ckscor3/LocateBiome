package bl4ckscor3.mod.locatebiome;

import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.ISuggestionProvider;
import net.minecraft.command.arguments.ResourceLocationArgument;
import net.minecraft.command.arguments.SuggestionProviders;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentUtils;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.network.FMLNetworkConstants;
import net.minecraftforge.registries.ForgeRegistries;

@Mod("locatebiome")
@EventBusSubscriber
public class LocateBiome
{
	private static final SuggestionProvider<CommandSource> ALL_BIOMES = SuggestionProviders.register(new ResourceLocation("locatebiome", "all_biomes"), (ctx, builder) -> {
		return ISuggestionProvider.suggestIterable(ForgeRegistries.BIOMES.getKeys(), builder);
	});
	private static final DynamicCommandExceptionType INVALID_EXCEPTION = new DynamicCommandExceptionType(obj -> new TranslationTextComponent("There is no biome named %s", obj));
	private static final DynamicCommandExceptionType NOT_FOUND_EXCEPTION = new DynamicCommandExceptionType(obj -> new TranslationTextComponent("Could not find a %s within reasonable distance", obj));

	public LocateBiome()
	{
		//client does not need mod installed
		ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.DISPLAYTEST, () -> Pair.of(() -> FMLNetworkConstants.IGNORESERVERONLY, (a, b) -> true));
	}

	@SubscribeEvent
	public static void onFMLServerStarting(FMLServerStartingEvent event)
	{
		event.getCommandDispatcher().register(Commands.literal("locatebiome")
				.requires(player -> player.hasPermissionLevel(2))
				.then(Commands.argument("biome", ResourceLocationArgument.resourceLocation())
						.suggests(ALL_BIOMES)
						.executes(ctx -> execute(ctx.getSource(), getBiomeFromArgument(ctx, "biome")))));
	}

	private static Biome getBiomeFromArgument(CommandContext<CommandSource> ctx, String arg) throws CommandSyntaxException
	{
		ResourceLocation biomeRl = ctx.getArgument(arg, ResourceLocation.class);
		Biome biome = ForgeRegistries.BIOMES.getValue(biomeRl);

		if(biome != null)
			return biome;
		else throw INVALID_EXCEPTION.create(biomeRl);
	}

	private static int execute(CommandSource src, Biome biome) throws CommandSyntaxException
	{
		BlockPos sourcePos = new BlockPos(src.getPos());
		BlockPos biomePos = locateBiome(src.getWorld(), biome, sourcePos, 6400, 8);
		TranslationTextComponent biomeName = new TranslationTextComponent(biome.getTranslationKey());

		if(biomePos != null)
		{
			int distance = MathHelper.floor(getDistance(sourcePos.getX(), sourcePos.getZ(), biomePos.getX(), biomePos.getZ()));

			src.sendFeedback(new TranslationTextComponent("commands.locate.success", biomeName,
					TextComponentUtils.wrapInSquareBrackets(new TranslationTextComponent("chat.coordinates", biomePos.getX(), "~", biomePos.getZ()))
					.applyTextStyle(style -> style.setColor(TextFormatting.GREEN)
							.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tp @s " + biomePos.getX() + " ~ " + biomePos.getZ()))
							.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TranslationTextComponent("chat.coordinates.tooltip")))), distance), false);
			return distance;
		}
		else throw NOT_FOUND_EXCEPTION.create(biomeName);
	}

	private static BlockPos locateBiome(ServerWorld world, Biome biome, BlockPos pos, int maxSearchRadius, int searchIncrement)
	{
		return locateBiome(world, pos.getX(), pos.getZ(), maxSearchRadius, searchIncrement, ImmutableList.of(biome));
	}

	//code pulled from snapshot versions using fabric's toolchain and tried to make legible as best as possible
	//there is a way to do this without copying code, however it is not performant for a high search radius
	private static BlockPos locateBiome(ServerWorld world, int x, int z, int maxSearchRadius, int searchIncrement, List<Biome> biomes)
	{
		int originX = x >> 2;
		int originZ = z >> 2;
		int border = maxSearchRadius >> 2;
		BlockPos pos = null;

		for(int idk = 0; idk <= border; idk += searchIncrement)
		{
			for(int zi = -idk; zi <= idk; zi += searchIncrement)
			{
				boolean flag1 = Math.abs(zi) == idk;

				for(int xi = -idk; xi <= idk; xi += searchIncrement)
				{
					boolean flag2 = Math.abs(xi) == idk;

					if(!flag2 && !flag1)
						continue;

					int currentX = originX + xi;
					int currentZ = originZ + zi;

					//find the biome at the current position
					pos = world.getChunkProvider().getChunkGenerator().getBiomeProvider().findBiomePosition(currentX << 2, currentZ << 2, 1, biomes, world.rand);

					if(pos != null)
						return pos;
				}
			}
		}

		return pos;
	}

	private static float getDistance(int x1, int y1, int x2, int y2)
	{
		int i = x2 - x1;
		int j = y2 - y1;
		return MathHelper.sqrt(i * i + j * j);
	}
}

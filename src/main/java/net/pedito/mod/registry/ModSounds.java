package net.pedito.mod.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.pedito.mod.Pedito;

public class ModSounds {

	public static SoundEvent PEDITO_FART;
	public static SoundEvent PEDITO_FART_SPAWN;
	public static SoundEvent PEDITO_VOICE_PEDI;
	public static SoundEvent PEDITO_VOICE_PEDITO;
	public static SoundEvent PEDITO_VOICE_PUPU;
	public static SoundEvent PEDITO_VOICE_PUPULLITO;
	public static SoundEvent PEDITO_VOICE_SWAN;
	public static SoundEvent PEDITO_VOICE_ATTACK;

	// New attack sounds
	public static SoundEvent PEDITO_DASH;
	public static SoundEvent PEDITO_EXPLOSION;
	public static SoundEvent PEDITO_LASER;
	public static SoundEvent PEDITO_SONIC_BOOM;
	public static SoundEvent PEDITO_SUMMON;
	public static SoundEvent PEDITO_VORTEX;

	public static SoundEvent PEDITO_STAFF;
	public static SoundEvent PEDITO_SPRAY;

	public static void register() {
		PEDITO_FART = registerSoundEvent("pedito_fart");
		PEDITO_FART_SPAWN = registerSoundEvent("pedito_fart_spawn");
		PEDITO_VOICE_PEDI = registerSoundEvent("pedito_voice_pedi");
		PEDITO_VOICE_PEDITO = registerSoundEvent("pedito_voice_pedito");
		PEDITO_VOICE_PUPU = registerSoundEvent("pedito_voice_pupu");
		PEDITO_VOICE_PUPULLITO = registerSoundEvent("pedito_voice_pupullito");
		PEDITO_VOICE_SWAN = registerSoundEvent("pedito_voice_swan");
		PEDITO_VOICE_ATTACK = registerSoundEvent("pedito_voice_attack");

		PEDITO_DASH = registerSoundEvent("pedito_dash");
		PEDITO_EXPLOSION = registerSoundEvent("pedito_explosion");
		PEDITO_LASER = registerSoundEvent("pedito_laser");
		PEDITO_SONIC_BOOM = registerSoundEvent("pedito_sonic_boom");
		PEDITO_SUMMON = registerSoundEvent("pedito_summon");
		PEDITO_VORTEX = registerSoundEvent("pedito_vortex");
		
		PEDITO_STAFF = registerSoundEvent("baculo");
		PEDITO_SPRAY = registerSoundEvent("spray");
	}

	private static SoundEvent registerSoundEvent(String name) {
		Identifier id = Identifier.fromNamespaceAndPath(Pedito.MOD_ID, name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}
}

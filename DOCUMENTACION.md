# Documentación de Peditos - Sistema de Voces y Sonidos

## Frases Habladas (Ambientales)
Los peditos tienen un sistema de sonido ambiental donde reproducen de manera aleatoria 5 frases principales:
- `pedito_voice_pedi` ("¡Pedi!")
- `pedito_voice_pedito` ("¡Pedito!")
- `pedito_voice_pupu` ("¡Pupu!")
- `pedito_voice_pupullito` ("¡Pupullito!")
- `pedito_voice_swan` ("¡Swan!")

Estas frases se reproducen aleatoriamente mientras el mob está en modo ambiental (idle), y se les asigna un tono (pitch) más agudo si el Pedito es bebé.

## Sonidos y Frases de Ataque
Cada tipo de ataque especial en enjambre tiene asignado un sistema de voces combinado con un efecto de sonido:

1. **Ataque Básico (PeditoAttackGoal):**
   - El pedito gritará al atacar (aleatoriamente, probabilidad 40%).
   - Se emite el sonido de pedo `pedito_fart` por CADA ataque básico realizado con un pitch variante.

2. **Pixel Lazer (Tier 1 - 1+ Peditos):**
   - Frase: Aleatoria al iniciar el láser.
   - Sonido: `pedito_laser` al disparar el rayo de colores.

3. **Rainbow Dash (Tier 2 - 2+ Peditos):**
   - Frase: El líder usa `pedito_voice_attack`.
   - Sonido: `pedito_dash` al impactar como misil arcoíris.

4. **Color Fusion (Tier 3 - 3+ Peditos):**
   - Frase: El anillo sincronizado grita con `pedito_voice_attack`.
   - Sonido: `pedito_explosion` al ejecutar la fusión de colores masiva.

5. **Sonic Boom (Tier 4 - 4+ Peditos):**
   - Frase: Carga con `pedito_voice_attack` antes de lanzar el aro.
   - Sonido: `pedito_sonic_boom` cuando disparan el aro supersónico.

6. **Cubic Vortex (Tier 5 - 5+ Peditos):**
   - Frase: `pedito_voice_attack` en un tono bajo al organizar el vórtice.
   - Sonido: `pedito_vortex` durante la ejecución del tornado cúbico atrapando al enemigo.

7. **Pedito Golem Summon (Tier 6 - 6+ Peditos):**
   - Frase: Grito grupal `pedito_voice_attack` intenso.
   - Sonido: `pedito_summon` al fusionarse para formar al Golem Pedito.

## Eventos de Sonido Adicionales
- **Spawn (Nacimiento/Aparición):** `pedito_fart_spawn`.
- **Interacciones Mágicas:**
  - El **Pedito Spray** usa `pedito_spray` (`spray` interno) al limpiar y bañar a los peditos dándoles el efecto Regeneración.


# Changelog

## 1.2.0 - Actualización 2
- NUEVO Colisión con Marte (aproximación 3D, vibración de cámara, impacto limitado al radio `marsImpactRadius`, crater con presupuesto de bloques por tick).
- NUEVO Huracán gigante móvil (embudo + nubarrones, velocidad/rumbo variables, levanta y arrastra entidades, arranca bloques débiles, lluvia/truenos/rayos).
- NUEVO Ola de calor extrema (domo + sol 3D, exposición acumulada por entidad, incendios limitados por config/doFireTick/lluvia).
- NUEVO Ola de congelación extrema (frente 3D, congela mobs/jugadores, hielo temporal registrado en `FrozenTerrainData` que restaura el bloque original solo si sigue siendo el hielo del evento).
- NUEVO OVNI + alienígena + Pistola Láser + Celda de Energía (receta, config, haz 3D compartido `LaserBeams`).
- Cliente: `CameraShakeHandler` (config de cliente), panel adaptable al tamaño de pantalla (22 desastres + aleatorio).

## 1.1.0 - Actualización 1
- FIX granizo: eliminado el segundo generador (granizo sobre cualquier jugador); un solo punto de generación limitado a la nube, tope de bolas vivas, sin spawn sin jugadores cerca.
- FIX tsunami: sin bloques de agua; ola 3D que sigue el relieve, empuja y daña; sin cambios en el mundo.
- NUEVO Cometa Halley (cráter, hielo controlado registrado, conversión a agua real a los 400 ticks, datos persistentes).
- NUEVO Agujero de gusano (entrada/salida, destino seguro ~100 bloques, sin copias ni pérdidas).
- NUEVO Agujero negro hipotético conectado a un agujero blanco (independiente del agujero negro original).
- Infra: `SafeSpot`, `TransportUtil`, registro de entidades en tránsito, `CometIceData` (SavedData).
- Idiomas actualizados (en_us y variantes es_*). Panel ajustado a 17 desastres.

## 1.0.0
- Versión inicial: 14 desastres, panel de control, comando /disaster, entidades 3D.

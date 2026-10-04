package com.mrtahadarvish.unstableffa;

import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Tameable;

import java.util.UUID;

/** Tamed pets must not outlive their owner's life in the arena. */
final class Pets {

    private Pets() { }

    /** Removes every tamed animal in the world that belongs to this player. */
    static int removeTamed(World world, UUID owner) {
        int removed = 0;
        for (Entity entity : world.getEntities()) {
            if (entity instanceof Tameable pet && pet.isTamed() && owner.equals(pet.getOwnerUniqueId())) {
                entity.remove();
                removed++;
            }
        }
        return removed;
    }
}

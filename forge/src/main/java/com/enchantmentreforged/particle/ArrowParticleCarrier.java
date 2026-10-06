package com.enchantmentreforged.particle;

/**
 * 箭矢上"射手选定的粒子档位"的访问接口，由 Mixin 实现在 {@code AbstractArrow} 上。
 *
 * <p>值是同步字段（随实体出生包一起送达所有客户端），因此每名玩家看到的
 * 都是"射箭那个人选的粒子"。值为 {@code -1} 表示这支箭没有携带射手档位
 * （生物、发射器、未装本模组的玩家射出的箭），此时观者按自己的设置渲染。
 */
public interface ArrowParticleCarrier {
	/** 读取这支箭携带的射手档位；-1 表示未携带 */
	int enchantmentReforged$getParticleStyle();

	/** 写入射手档位（只在服务端生成箭时调用） */
	void enchantmentReforged$setParticleStyle(int style);
}

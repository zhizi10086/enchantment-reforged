package com.enchantmentreforged.combat;

/**
 * 由 {@code TridentEntityMixin} 实现的鸭子接口。
 *
 * <p>忠诚改版后，投掷出去的三叉戟只是"假三叉戟"：本体仍留在玩家物品栏里。
 * 假三叉戟需要能被识别出来，才能做到"返回前不能再次投掷"、"飞回来只消失不复制"。
 */
public interface PhantomTrident {
	/** 标记/取消"假三叉戟"（生成时标记，读数时判断） */
	void enchantmentReforged$setPhantom(boolean phantom);

	/**
	 * 这把三叉戟是否带忠诚。
	 *
	 * <p>用同步数据判断，因此客户端也能得到与服务端一致的结论：
	 * "我身上还有一把忠诚三叉戟在飞"就是"假三叉戟还没回来"。
	 */
	boolean enchantmentReforged$isLoyal();

	/**
	 * 假三叉戟已经飞了多少 tick（自管理计数，不依赖原版的 age）。
	 *
	 * <p>拦截投掷需要一个"无论如何都会到期"的上限：万一假三叉戟因为跨维度、
	 * 区块长期不加载等原因一直不回收，也能按这个计数放行，绝不会把玩家卡死。
	 */
	int enchantmentReforged$flightTicks();
}

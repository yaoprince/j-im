/**
 * 
 */
package org.jim.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;

import org.apache.commons.lang3.StringUtils;
import org.jim.common.cluster.ImCluster;
import org.jim.common.listener.ImBindListener;
import org.jim.common.packets.Client;
import org.jim.common.packets.User;
import org.jim.common.utils.ImKit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tio.core.Aio;
import org.tio.core.ChannelContext;
import org.tio.core.GroupContext;
import org.tio.utils.lock.SetWithLock;
/**
 * 版本: [1.0]
 * 功能说明: 
 * 作者: WChao 创建时间: 2017年9月22日 上午9:07:18
 */
public class ImAio {
	
	private static GroupContext groupContext = ImConfig.groupContext;
	
	private static Logger log = LoggerFactory.getLogger(ImAio.class);
	/**
	 * 功能描述：[根据用户ID获取当前用户]
	 * 创建者：WChao 创建时间: 2017年9月18日 下午4:34:39
	 * @param groupContext
	 * @param userid
	 * @return
	 */
	public static User getUser(String userid){
		SetWithLock<ChannelContext> userChannelContexts = ImAio.getChannelContextsByUserid(userid);
		if(userChannelContexts == null)
			return null;
		ReadLock readLock = userChannelContexts.getLock().readLock();
		readLock.lock();
		try{
			Set<ChannelContext> userChannels = userChannelContexts.getObj();
			if(userChannels == null )
				return null;
			User user = null;
			for(ChannelContext channelContext : userChannels){
				ImSessionContext imSessionContext = (ImSessionContext)channelContext.getAttribute();
				Client client = imSessionContext.getClient();
				user = client.getUser();
				if(user != null){
					return user;
				}
			}
			return user;
		}finally {
			readLock.unlock();
		}
	}
	/**
	 * 
		 * 功能描述：[根据用户ID获取当前用户所在通道集合]
		 * 创建者：WChao 创建时间: 2017年9月18日 下午4:34:39
		 * @param groupContext
		 * @param userid
		 * @return
		 *
	 */
	public static SetWithLock<ChannelContext> getChannelContextsByUserid(String userid){
		SetWithLock<ChannelContext> channelContexts = Aio.getChannelContextsByUserid(groupContext, userid);
		return channelContexts;
	}
	/**
	 * 
		 * 功能描述：[获取所有用户(在线+离线)]
		 * 创建者：WChao 创建时间: 2017年9月18日 下午4:31:54
		 * @param groupContext
		 * @return
		 *
	 */
	public static List<User> getAllUser(){
		List<User> users = new ArrayList<User>();
		SetWithLock<ChannelContext> allChannels = Aio.getAllChannelContexts(groupContext);
		if(allChannels == null)
			return users;
		ReadLock readLock = allChannels.getLock().readLock();
		readLock.lock();
		try{
			Set<ChannelContext> userChannels = allChannels.getObj();
			if(userChannels == null)
				return users;
			for(ChannelContext channelContext : userChannels){
				ImSessionContext imSessionContext = (ImSessionContext)channelContext.getAttribute();
				Client client = imSessionContext.getClient();
				if(client != null && client.getUser() != null){
					User user = ImKit.copyUserWithoutUsers(client.getUser());
					users.add(user);
				}
			}
		}finally {
			readLock.unlock();
		}
		return users;
	}
	/**
	 * 
		 * 功能描述：[获取所有在线用户]
		 * 创建者：WChao 创建时间: 2017年9月18日 下午4:31:42
		 * @param groupContext
		 * @return
		 *
	 */
	public static List<User> getAllOnlineUser(){
		List<User> users = new ArrayList<User>();
		SetWithLock<ChannelContext> onlineChannels = Aio.getAllConnectedsChannelContexts(groupContext);
		if(onlineChannels == null)
			return users;
		ReadLock readLock = onlineChannels.getLock().readLock();
		readLock.lock();
		try{
			Set<ChannelContext> userChannels = onlineChannels.getObj();
			for(ChannelContext channelContext : userChannels){
				ImSessionContext imSessionContext = (ImSessionContext)channelContext.getAttribute();
				if(imSessionContext != null){
					Client client = imSessionContext.getClient();
					if(client != null && client.getUser() != null){
						User user = ImKit.copyUserWithoutUsers(client.getUser());
						users.add(user);
					}
				}
			}
		}finally {
			readLock.unlock();
		}
		return users;
	}
	/**
	 * 根据群组获取所有用户;
	 * @param group
	 * @return
	 */
	public static List<User> getAllUserByGroup(String group){
		SetWithLock<ChannelContext> withLockChannels = Aio.getChannelContextsByGroup(groupContext, group);
		if(withLockChannels == null){
			return null;
		}
		ReadLock readLock = withLockChannels.getLock().readLock();
		readLock.lock();
		try{
			Set<ChannelContext> channels = withLockChannels.getObj();
			if(channels != null && channels.size() > 0){
				List<User> users = new ArrayList<User>();
				Map<String,User> userMaps = new HashMap<String,User>();
				for(ChannelContext channelContext : channels){
					String userid = channelContext.getUserid();
					User user = getUser(userid);
					if(user != null){
						if(userMaps.get(userid) == null){
							userMaps.put(userid, user);
							users.add(user);
						}
					}
				}
				userMaps = null;
				return users;
			}
			return null;
		}finally{
			readLock.unlock();
		}
	}
	/**
	 * 功能描述：[发送到群组(所有不同协议端)]
	 * 创建者：WChao 创建时间: 2017年9月21日 下午3:26:57
	 * @param groupContext
	 * @param group
	 * @param packet
	 */
	public static void sendToGroup(String group, ImPacket packet){
		if(packet.getBody() == null)
			return;
		SetWithLock<ChannelContext> withLockChannels = Aio.getChannelContextsByGroup(groupContext, group);
		if(withLockChannels == null){
			ImCluster cluster = ImConfig.cluster;
			if (cluster != null && !packet.isFromCluster()) {
				cluster.clusterToGroup(groupContext, group, packet);
			}
			return;
		}
		ReadLock readLock = withLockChannels.getLock().readLock();
		readLock.lock();
		try{
			Set<ChannelContext> channels = withLockChannels.getObj();
			if(channels != null && channels.size() > 0){
				for(ChannelContext channelContext : channels){
					send(channelContext,packet);
				}
			}
		}finally{
			readLock.unlock();
			ImCluster cluster = ImConfig.cluster;
			if (cluster != null && !packet.isFromCluster()) {
				cluster.clusterToGroup(groupContext, group, packet);
			}
		}
	}
	/**
	 * 发送到指定通道;
	 * @param toChannleContexts
	 * @param packet
	 */
	public static boolean send(ChannelContext channelContext,ImPacket packet){
		if(channelContext == null)
			return false;
		ImPacket rspPacket = ImKit.ConvertRespPacket(packet, packet.getCommand(), channelContext);
		if(rspPacket == null){
			log.error("转换协议包为空,请检查协议！");
			return false;
		}
		rspPacket.setSynSeq(packet.getSynSeq());
		if(groupContext == null){
			groupContext = channelContext.getGroupContext();
		}
		return sendToId(channelContext.getId(), rspPacket);
	}
	/**
	 * 发消息给指定ChannelContext id
	 * @param channelId
	 * @param packet
	 * @return
	 */
	public static Boolean sendToId(String channelId, ImPacket packet) {
		ChannelContext channelContext = Aio.getChannelContextById(groupContext, channelId);
		if (channelContext == null) {
			ImCluster cluster = ImConfig.cluster;
			if (cluster != null && !packet.isFromCluster()) {
				cluster.clusterToChannelId(groupContext, channelId, packet);
			}
		}
		return Aio.sendToId(groupContext, channelId, packet);
	}
	/**
	 * 发送到指定用户;
	 * @param toChannleContexts
	 * @param packet
	 */
	public static void sendToUser(String userid,ImPacket packet){
		if(StringUtils.isEmpty(userid))
			return;
		SetWithLock<ChannelContext> toChannleContexts = getChannelContextsByUserid(userid);
		if(toChannleContexts == null || toChannleContexts.size() < 1){
			ImCluster cluster = ImConfig.cluster;
			if (cluster != null && !packet.isFromCluster()) {
				cluster.clusterToUser(groupContext, userid, packet);
			}
			return;
		}
		ReadLock readLock = toChannleContexts.getLock().readLock();
		readLock.lock();
		try{
			Set<ChannelContext> channels = toChannleContexts.getObj();
			for(ChannelContext channelContext : channels){
				send(channelContext, packet);
			}
		}finally{
			readLock.unlock();
			ImCluster cluster = ImConfig.cluster;
			if (cluster != null && !packet.isFromCluster()) {
				cluster.clusterToUser(groupContext, userid, packet);
			}
		}
	}
	/**
	 * 发送到指定ip对应的集合
	 * @param groupContext
	 * @param ip
	 * @param packet
	 * @author: tanyaowu
	 */
	public static void sendToIp(GroupContext groupContext, String ip, ImPacket packet) {
		try{
			Aio.sendToIp(groupContext, ip, packet, null);
		}finally{
			ImCluster cluster = ImConfig.cluster;
			if (cluster != null && !packet.isFromCluster()) {
				cluster.clusterToIp(groupContext, ip, packet);
			}
		}
	}
	/**
	 * 绑定用户;
	 * @param channelContext
	 * @param userid
	 */
	public static void bindUser(ChannelContext channelContext,String userid){
		bindUser(channelContext, userid,null);
	}
	/**
	 * 绑定用户,同时可传递监听器执行回调函数
	 * @param channelContext
	 * @param userid
	 * @param bindListener(绑定监听器回调)
	 */
	public static void bindUser(ChannelContext channelContext,String userid,ImBindListener bindListener){
		Aio.bindUser(channelContext, userid);
		if(bindListener != null){
			try {
				bindListener.onAfterUserBind(channelContext, userid);
			} catch (Exception e) {
				log.error(e.toString(),e);
			}
		}
	}
	/**
	 * 解绑用户
	 * @param groupContext
	 * @param userid
	 */
	public static void unbindUser(String userid){
		unbindUser(userid, null);
	}
	/**
	 * 解除绑定用户,同时可传递监听器执行回调函数
	 * @param channelContext
	 * @param userid
	 * @param bindListener(解绑定监听器回调)
	 */
	public static void unbindUser(String userid,ImBindListener bindListener){
		Aio.unbindUser(groupContext, userid);
		if(bindListener != null){
			try {
				SetWithLock<ChannelContext> userChannelContexts = ImAio.getChannelContextsByUserid(userid);
				if(userChannelContexts == null || userChannelContexts.size() == 0)
					return ;
				ReadLock readLock = userChannelContexts.getLock().readLock();
				readLock.lock();
				try{
					Set<ChannelContext> channels = userChannelContexts.getObj();
					for(ChannelContext channelContext : channels){
						bindListener.onAfterUserBind(channelContext, userid);
					}
				}finally{
					readLock.unlock();
				}
			} catch (Exception e) {
				log.error(e.toString(),e);
			}
		}
	}
	/**
	 * 绑定群组;
	 * @param channelContext
	 * @param group
	 */
	public static void bindGroup(ChannelContext channelContext,String group){
		bindGroup(channelContext, group,null);
	}
	/**
	 * 绑定群组,同时可传递监听器执行回调函数
	 * @param channelContext
	 * @param group
	 * @param binListener(绑定监听器回调)
	 */
	public static void bindGroup(ChannelContext channelContext,String group,ImBindListener bindListener){
		Aio.bindGroup(channelContext, group);
		if(bindListener != null){
			try {
				bindListener.onAfterGroupBind(channelContext, group);
			} catch (Exception e) {
				log.error(e.toString(),e);
			}
		}
	}
	/**
	 * 与指定群组解除绑定
	 * @param userid
	 * @param group
	 * @param bindListener
	 */
	public static void unbindGroup(String userid,String group){
		unbindGroup(userid, group, null);
	}
	/**
	 * 与指定群组解除绑定,同时可传递监听器执行回调函数
	 * @param channelContext
	 * @param group
	 * @param binListener(解绑定监听器回调)
	 */
	public static void unbindGroup(String userid,String group,ImBindListener bindListener){
		SetWithLock<ChannelContext> userChannelContexts = ImAio.getChannelContextsByUserid(userid);
		if(userChannelContexts == null || userChannelContexts.size() == 0)
			return ;
		ReadLock readLock = userChannelContexts.getLock().readLock();
		readLock.lock();
		try{
			Set<ChannelContext> channels = userChannelContexts.getObj();
			for(ChannelContext channelContext : channels){
				Aio.unbindGroup(group, channelContext);
				if(bindListener != null){
					try {
						bindListener.onAfterGroupUnbind(channelContext, group);
					} catch (Exception e) {
						log.error(e.toString(),e);
					}
				}
			}
		}finally{
			readLock.unlock();
		}
	}
	/**
	 * 移除用户, 和close方法一样，只不过不再进行重连等维护性的操作
	 * @param userid
	 * @param remark
	 */
	public static void remove(String userid,String remark){
		SetWithLock<ChannelContext> userChannelContexts = getChannelContextsByUserid(userid);
		if(userChannelContexts != null && userChannelContexts.size() > 0){
			ReadLock readLock = userChannelContexts.getLock().readLock();
			readLock.lock();
			try{
				Set<ChannelContext> channels = userChannelContexts.getObj();
				for(ChannelContext channelContext : channels){
					remove(channelContext, remark);
				}
			}finally{
				readLock.unlock();
			}
		}
	}
	/**
	 * 移除指定channel, 和close方法一样，只不过不再进行重连等维护性的操作
	 * @param userid
	 * @param remark
	 */
	public static void remove(ChannelContext channelContext,String remark){
		Aio.remove(channelContext, remark);
	}
}

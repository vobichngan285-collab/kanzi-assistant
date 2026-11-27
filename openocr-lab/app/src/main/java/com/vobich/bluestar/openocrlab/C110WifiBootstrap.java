package com.vobich.bluestar.openocrlab;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.RouteInfo;
import java.net.Inet4Address;
/** Manual system-Wi-Fi only, no automatic pairing, no mobile or cloud binding. */
final class C110WifiBootstrap {
 static Network preferredNetwork(ConnectivityManager manager){return null;}
 static Network connected(Context context){
  ConnectivityManager manager=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
  if(manager==null)return null;
  for(Network n:manager.getAllNetworks()){
   NetworkCapabilities caps=manager.getNetworkCapabilities(n);
   if(caps==null||!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))continue;
   LinkProperties props=manager.getLinkProperties(n);
   if(props==null)continue;
   for(RouteInfo route:props.getRoutes())
    if(route.isDefaultRoute()&&route.getGateway() instanceof Inet4Address&&C110WifiCamera.CAMERA_IP.equals(route.getGateway().getHostAddress()))return n;
  }
  return null;
 }
}

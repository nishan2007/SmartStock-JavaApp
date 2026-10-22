package gy.deckers.studio;

import services.BadgeCredentialService;
import services.LanApiClient;
import services.PcscNfcService;
import java.time.Duration;

/** Capture only an encoded employee credential; card UIDs are never login credentials. */
final class StudioBadgeCapture {
  interface Reader { boolean available(); String read(Duration timeout) throws Exception; }
  interface Policy { LanApiClient.BadgeStatus check(String badge,int location) throws Exception; }
  record Captured(String identifier,LanApiClient.BadgeStatus policy) {
    @Override public String toString(){return "Captured employee badge";}
  }
  private static Reader deviceReader(){return new Reader(){
    public boolean available(){return PcscNfcService.hasReader();}
    public String read(Duration timeout)throws Exception{return PcscNfcService.read(timeout).payload();}
  };}
  static Captured capture(int location) throws Exception {
    return capture(location,deviceReader(),LanApiClient::badgeStatus);
  }
  static Captured poll(int location,String previous)throws Exception {
    return poll(location,previous,deviceReader(),LanApiClient::badgeStatus);
  }
  static Captured poll(int location,String previous,Reader reader,Policy policy)throws Exception {
    if(location<=0)throw new IllegalArgumentException("Pair this computer with your store first.");
    if(!reader.available())return null;
    return resolve(location,reader.read(Duration.ofSeconds(2)),previous,policy);
  }
  static Captured capture(int location,Reader reader,Policy policy)throws Exception {
    if(location<=0)throw new IllegalArgumentException("Pair this computer with your store first.");
    if(!reader.available())throw new IllegalStateException("No compatible PC/SC NFC reader found. Connect the reader and check its Windows driver.");
    return resolve(location,reader.read(Duration.ofSeconds(20)),null,policy);
  }
  private static Captured resolve(int location,String payload,String previous,Policy policy)throws Exception {
    String badge=BadgeCredentialService.normalizeBadge(payload);
    if(!BadgeCredentialService.looksLikeGeneratedBadge(badge))throw new IllegalArgumentException("This card is not a SmartStock employee badge.");
    if(badge.equals(previous))return null;
    return new Captured(badge,policy.check(badge,location));
  }
}

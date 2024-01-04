package com.legacyminecraft.authentication;
import com.johnymuffin.evolutions.core.BetaEvolutionsUtils;

public class AuthenticationThread extends Thread {
   private String username;
   private String sessionID;
   private String mojangAuthURL;

   public AuthenticationThread(String username, String sessionID) {
      this.username = username;
      this.sessionID = sessionID;
      this.mojangAuthURL = this.mojangAuthURL;
   }

   @Override
   public void run() {
      while(true) {
         try {
            BetaEvolutionsUtils betaEvolutionsUtils = new BetaEvolutionsUtils(true);
            BetaEvolutionsUtils.VerificationResults results = betaEvolutionsUtils.authenticateUser(this.username, this.sessionID);
            System.out
               .println(
                  "Beta Evolutions Stats, Successful: "
                     + results.getSuccessful()
                     + ", Failed: "
                     + results.getFailed()
                     + ", Errored: "
                     + results.getErrored()
                     + ", Total: "
                     + results.getTotal()
               );
         } catch (Exception var4) {
            System.out.println("An error occurred authing with Beta Evolutions: ");
            var4.printStackTrace();
         }

         try {
            Thread.sleep(86400000L);
         } catch (InterruptedException var3) {
         }
      }
   }
}

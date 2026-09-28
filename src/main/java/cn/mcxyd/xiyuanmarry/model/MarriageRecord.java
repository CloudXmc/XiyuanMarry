package cn.mcxyd.xiyuanmarry.model;
import java.util.UUID;
public record MarriageRecord(UUID playerOne,UUID playerTwo,MarriageState state,String type,long createdAt,long divorceAt,long bond,String id,long marriedAt,long totalBond,long sharedSeconds){
 public boolean contains(UUID id){return playerOne.equals(id)||playerTwo.equals(id);}
 public UUID partnerOf(UUID id){if(playerOne.equals(id))return playerTwo;if(playerTwo.equals(id))return playerOne;throw new IllegalArgumentException("成员不属于此婚姻");}
 public boolean married(){return state==MarriageState.MARRIED||state==MarriageState.DIVORCE_PENDING;}
}


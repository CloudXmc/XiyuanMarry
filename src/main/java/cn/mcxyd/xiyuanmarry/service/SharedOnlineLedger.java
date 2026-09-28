package cn.mcxyd.xiyuanmarry.service;
import cn.mcxyd.xiyuanmarry.repository.MarriageRepository;
import cn.mcxyd.xiyuanmarry.model.MarriageState;
import com.google.gson.Gson;
import java.time.LocalDate;
import java.util.UUID;
/** start/end 为纪元秒；关系级高水位与金币无关，只事务发放羁绊。 */
public final class SharedOnlineLedger {
    private final Gson gson=new Gson();
    private record State(long lastEnd,String awardedDate) {}
    public record Award(long seconds,long bond) {}
    public Award record(MarriageRepository r,UUID actor,String relation,long start,long end,String date,long hourlyBond,long dailyBond){
        if(actor==null||relation==null||date==null||start<0||end<=start||end-start>5||end>Long.MAX_VALUE/1000)return new Award(0,0);
        LocalDate.parse(date);
        if(hourlyBond<0||hourlyBond>1000000||dailyBond<0||dailyBond>1000000)throw new IllegalArgumentException("在线羁绊奖励超出范围");
        return r.transaction(tx->{
            var marriage=tx.findByPlayer(actor);
            if(marriage==null||!marriage.married()||!marriage.id().equals(relation)
                ||marriage.state()==MarriageState.DIVORCE_PENDING&&marriage.divorceAt()<=end*1000)return new Award(0,0);
            String raw=tx.get("shared-online",relation);
            State state=raw==null?new State(0,""):gson.fromJson(raw,State.class);
            long effectiveStart=Math.max(Math.max(start,state.lastEnd()),(marriage.marriedAt()+999)/1000);
            long seconds=Math.max(0,end-effectiveStart);
            if(seconds==0)return new Award(0,0);
            long after=Math.addExact(marriage.sharedSeconds(),seconds);
            long bond=Math.multiplyExact(after/3600-marriage.sharedSeconds()/3600,hourlyBond);
            String awarded=state.awardedDate();
            if(date.compareTo(awarded)>0){bond=Math.addExact(bond,dailyBond);awarded=date;}
            Math.addExact(marriage.bond(),bond);Math.addExact(marriage.totalBond(),bond);
            if(!tx.addOnline(actor,seconds))throw new IllegalStateException("共同在线时间提交失败");
            if(bond>0&&!tx.addBond(actor,bond))throw new IllegalStateException("共同在线奖励提交失败");
            tx.put("shared-online",relation,gson.toJson(new State(end,awarded)));
            return new Award(seconds,bond);
        });
    }
}

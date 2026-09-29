package org.example.triggerservice.util;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Before/after text of an audited change. Only fields whose value changed are
 * listed, e.g. "Threshold: 150 mm" -> "Threshold: 200 mm".
 */
public final class AuditDiff{
    private final List<String> before=new ArrayList<>();
    private final List<String> after=new ArrayList<>();

    private AuditDiff(){}

    /** Compares two label-to-value maps built the same way (insertion order is kept). */
    public static AuditDiff of(Map<String,?> oldValues,Map<String,?> newValues){
        AuditDiff diff=new AuditDiff();
        oldValues.forEach((label,oldValue)->diff.field(label,oldValue,newValues.get(label)));
        return diff;
    }

    public static AuditDiff create(){
        return new AuditDiff();
    }

    public AuditDiff field(String label,Object oldValue,Object newValue){
        String oldText=format(oldValue);
        String newText=format(newValue);
        if(!Objects.equals(oldText,newText)){
            before.add(label+": "+(oldText==null?"—":oldText));
            after.add(label+": "+(newText==null?"—":newText));
        }
        return this;
    }

    public boolean isEmpty(){
        return before.isEmpty();
    }

    public String before(){
        return isEmpty()?null:String.join("; ",before);
    }

    public String after(){
        return isEmpty()?null:String.join("; ",after);
    }

    private static String format(Object value){
        if(value==null){
            return null;
        }
        if(value instanceof BigDecimal number){
            return number.stripTrailingZeros().toPlainString();
        }
        String text=value.toString().trim();
        return text.isEmpty()?null:text;
    }
}

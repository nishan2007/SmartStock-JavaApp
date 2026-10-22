package gy.deckers.studio;

import java.util.*;
import java.util.function.Predicate;

/** Object lists paint from back to front. Selection follows object identity. */
final class StudioLayerOrder {
    enum Move { FORWARD, BACKWARD, FRONT, BACK }
    record Result(boolean changed, int[] selection) {}
    static <T> Result move(List<T> objects, int[] indices, Move move, Predicate<T> locked) {
        Set<T> selected=Collections.newSetFromMap(new IdentityHashMap<>());
        for(int index:indices)if(index>=0&&index<objects.size())selected.add(objects.get(index));
        Set<T> movable=Collections.newSetFromMap(new IdentityHashMap<>());
        for(T item:selected)if(!locked.test(item))movable.add(item);
        List<T> before=new ArrayList<>(objects);
        if(move==Move.FORWARD){
            for(int i=objects.size()-2;i>=0;i--)if(movable.contains(objects.get(i))&&!movable.contains(objects.get(i+1)))Collections.swap(objects,i,i+1);
        }else if(move==Move.BACKWARD){
            for(int i=1;i<objects.size();i++)if(movable.contains(objects.get(i))&&!movable.contains(objects.get(i-1)))Collections.swap(objects,i,i-1);
        }else{
            List<T> moving=new ArrayList<>(),others=new ArrayList<>();
            for(T item:objects)(movable.contains(item)?moving:others).add(item);
            objects.clear();objects.addAll(move==Move.FRONT?others:moving);objects.addAll(move==Move.FRONT?moving:others);
        }
        int[] selection=java.util.stream.IntStream.range(0,objects.size()).filter(i->selected.contains(objects.get(i))).toArray();
        return new Result(!before.equals(objects),selection);
    }
}

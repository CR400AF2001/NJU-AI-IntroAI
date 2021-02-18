/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package controllers.learningmodel;

import tools.*;
import core.game.Observation;
import core.game.StateObservation;

import java.io.FileWriter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Observable;

import ontology.Types;
import weka.core.Attribute;
import weka.core.FastVector;
import weka.core.Instance;
import weka.core.Instances;

/**
 *
 * @author yuy
 */
public class RLDataExtractor {
    public FileWriter filewriter;
    public static Instances s_datasetHeader = datasetHeader();

    public RLDataExtractor(String filename) throws Exception{

        filewriter = new FileWriter(filename+".arff");
        filewriter.write(s_datasetHeader.toString());
        /*
                // ARFF File header
        filewriter.write("@RELATION AliensData\n");
        // Each row denotes the feature attribute
        // In this demo, the features have four dimensions.
        filewriter.write("@ATTRIBUTE gameScore  NUMERIC\n");
        filewriter.write("@ATTRIBUTE avatarSpeed  NUMERIC\n");
        filewriter.write("@ATTRIBUTE avatarHealthPoints NUMERIC\n");
        filewriter.write("@ATTRIBUTE avatarType NUMERIC\n");
        // objects
        for(int y=0; y<14; y++)
            for(int x=0; x<32; x++)
                filewriter.write("@ATTRIBUTE object_at_position_x=" + x + "_y=" + y + " NUMERIC\n");
        // The last row of the ARFF header stands for the classes
        filewriter.write("@ATTRIBUTE Class {0,1,2}\n");
        // The data will recorded in the following.
        filewriter.write("@Data\n");*/

    }

    public static Instance makeInstance(double[] features, int action, double reward){
        features[880] = action;
        features[881] = reward;
        Instance ins = new Instance(1, features);
        ins.setDataset(s_datasetHeader);
        return ins;
    }

    public static double[] featureExtract(StateObservation obs){

        double[] feature = new double[882];  // 868 + 12 + 1(action) + 1(Q)

        // 448 locations
        int[][] map = new int[28][31];
        // Extract features
        Vector2d avatarPos=obs.getAvatarPosition();
        double avatarX = avatarPos.x;
        double avatarY = avatarPos.y;
        boolean up = true;
        double distanceX = 0;
        double distanceY = 0;
        double sameMoving = 10000;
        double frontMoving = 10000;
        double frontImmoving = 10000;
        LinkedList<Observation> allobj = new LinkedList<>();
        if(obs.getImmovablePositions() != null){
            for(ArrayList<Observation> l : obs.getImmovablePositions()){
                allobj.addAll(l);
                for(Observation o:l){
                    if(o.position.x == avatarX && o.position.y + 28 == avatarY){
                        up = false;
                    }
                    if(avatarY == o.position.y + 28){
                        frontImmoving = Math.min(frontImmoving, Math.abs(o.position.x - avatarX));
                    }
                }
            }
        }
        if(obs.getMovablePositions() != null){
            for(ArrayList<Observation> l : obs.getMovablePositions()) {
                allobj.addAll(l);
                for (Observation o : l) {
                    if (o.position.y == avatarY) {
                        if(avatarY >= 196){
                            if(avatarX - o.position.x >= 0){
                                sameMoving = Math.min(sameMoving, avatarX - o.position.x);
                            }
                        }
                        else{
                            if(o.position.x - avatarX >= 0){
                                sameMoving = Math.min(sameMoving, o.position.x - avatarX);
                            }
                        }
                    }
                    if (o.position.y + 28 == avatarY) {
                        if(avatarY >= 224){
                            if(avatarX - o.position.x >= 0){
                                frontMoving = Math.min(frontMoving, avatarX - o.position.x);
                            }
                        }
                        else{
                            if(o.position.x - avatarX >= 0){
                                frontMoving = Math.min(frontMoving, o.position.x - avatarX);
                            }
                        }
                    }

                }
            }
        }
        if(obs.getNPCPositions() != null){
            for(ArrayList<Observation> l : obs.getNPCPositions()) allobj.addAll(l);
        }
        if(obs.getPortalsPositions()!=null) {
            for (ArrayList<Observation> l : obs.getPortalsPositions()) {
                allobj.addAll(l);
            }
        }


        for(Observation o : allobj){
            Vector2d p = o.position;
            int x = (int)(p.x/28); //squre size is 20 for pacman
            int y= (int)(p.y/28);  //size is 28 for FreeWay
            map[x][y] = o.itype;
            if(o.itype == 4) {
                distanceX = avatarX - o.position.x;
                distanceY = avatarY - o.position.y;
            }

        }
        for(int y=0; y<31; y++)
            for(int x=0; x<28; x++)
                feature[y*28+x] = map[x][y];

        // 4 states
        feature[868] = obs.getGameTick();
        feature[869] = obs.getAvatarSpeed();
        feature[870] = obs.getAvatarHealthPoints();
        feature[871] = obs.getAvatarType();
        feature[872] = avatarX;
        feature[873] = avatarY;
        feature[874] = up ? 1000.0 : -1000.0;
        feature[875] = distanceX;
        feature[876] = distanceY;
        feature[877] = sameMoving;
        feature[878] = frontMoving;
        feature[879] = frontImmoving;

        return feature;
    }

    public static Instances datasetHeader(){

        if (s_datasetHeader!=null)
            return s_datasetHeader;

        FastVector attInfo = new FastVector();
        // 448 locations
        for(int y=0; y<28; y++){
            for(int x=0; x<31; x++){
                Attribute att = new Attribute("object_at_position_x=" + x + "_y=" + y);
                attInfo.addElement(att);
            }
        }
        Attribute att = new Attribute("GameTick" ); attInfo.addElement(att);
        att = new Attribute("AvatarSpeed" ); attInfo.addElement(att);
        att = new Attribute("AvatarHealthPoints" ); attInfo.addElement(att);
        att = new Attribute("AvatarType" ); attInfo.addElement(att);
        att = new Attribute("avatarX" ); attInfo.addElement(att);
        att = new Attribute("avatarY" ); attInfo.addElement(att);
        att = new Attribute("up");attInfo.addElement(att);
        att = new Attribute("distanceX");attInfo.addElement(att);
        att = new Attribute("distanceY");attInfo.addElement(att);
        att = new Attribute("sameMoving");attInfo.addElement(att);
        att = new Attribute("frontMoving");attInfo.addElement(att);
        att = new Attribute("frontImmoving");attInfo.addElement(att);
        //action
        FastVector actions = new FastVector();
        actions.addElement("0");
        actions.addElement("1");
        actions.addElement("2");
        actions.addElement("3");
        att = new Attribute("actions", actions);
        attInfo.addElement(att);
        // Q value
        att = new Attribute("Qvalue");
        attInfo.addElement(att);

        Instances instances = new Instances("PacmanQdata", attInfo, 0);
        instances.setClassIndex( instances.numAttributes() - 1);

        return instances;
    }

}

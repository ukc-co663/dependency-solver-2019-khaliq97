package depsolver;

import java.util.*;

import com.microsoft.z3.*;


public class Resolver {


    private List<String> constraints;
   static private List<Package> repo;

    private Context context;
    private Solver solver;

    private List<Package> usedRepo;

    List<BoolExpr> finalExpr;

    private List<FinalStatePackage> finalStatePackages;


    public Resolver(List<String> constraintsFile, List<Package> packagesFile)
    {

        finalExpr = new ArrayList<>();
        constraints = constraintsFile;
        repo = packagesFile;
        finalStatePackages = new ArrayList<>();
        usedRepo = new ArrayList<>();
        context = new Context();
        solver = context.mkSolver();


       /* for(Package p: getAllPackageVersions("B>=3.1"))
        {
            System.out.println(p.getName() + p.getVersion());
        }*/

        resolve();

        System.out.println("----------------------------");
        getAllPackDepsAndCons();

        for(FinalStatePackage f: finalStatePackages)
        {
            FinalStatePackage finalStatePackage = f;
            f.setDeps(finalStatePackages);
            System.out.println(finalStatePackage.toString());

        }



    }

    public void getAllPackDepsAndCons()
    {
      for(FinalStatePackage s: finalStatePackages)
      {
          System.out.println(s.getPackageName() + " | " + s.getPackageVersionNumber());
      }
    }


    public void resolve()
    {

        for(String con: constraints)
        {
            Character packageOperation = con.charAt(0);

            switch (packageOperation)
            {
                case '+':

                    String[] installPackageOperation = con.split("\\" + packageOperation);

                    List<Package> allCurrentPackages = getAllPackageVersions(String.valueOf(installPackageOperation[1]));
                    List<BoolExpr> includePackages = new ArrayList<>();
                    List<BoolExpr> implyBoolExprs = new ArrayList<>();


                    for(Package p: allCurrentPackages)
                    {
                        BoolExpr curPackageBoolConst = context.mkBoolConst(p.getName() + "=" + p.getVersion());
                        includePackages.add(curPackageBoolConst);

                        usedRepo.add(p);

                        List<BoolExpr> implies  = getAllDependencyConstraints(p.getDepends());

                        BoolExpr conflictExpr = getAllConflictConstraints(p.getConflicts());
                        implies.add(conflictExpr);

                        implyBoolExprs.add(context.mkImplies(curPackageBoolConst, context.mkAnd(implies.toArray(new BoolExpr[implies.size()]))));



                        finalExpr.add(context.mkOr(includePackages.toArray(new BoolExpr[includePackages.size()])));
                        finalExpr.add(context.mkOr(implyBoolExprs.toArray(new BoolExpr[implyBoolExprs.size()])));



                    }

                    break;


                case '-':

                    String[] packageUninstallOperation = con.split("\\" + (packageOperation));


                    List<Package> _allCurrentPackages = getAllPackageVersions(String.valueOf(packageUninstallOperation));
                    List<BoolExpr> excludePackagesExpr = new ArrayList<>();

                    for(Package p: _allCurrentPackages)
                    {
                        usedRepo.add(p);
                        BoolExpr curPackageBoolConst = context.mkBoolConst(p.getName() + "=" + p.getVersion());
                        excludePackagesExpr.add(context.mkNot(curPackageBoolConst));
                    }


                    break;
            }


        }

        for(Package p: repo)
        {


            if(!usedRepo.contains(p))
            {
                BoolExpr curPackageBoolConst = context.mkBoolConst(p.getName() + "=" + p.getVersion());

                List<BoolExpr> implies  = getAllDependencyConstraints(p.getDepends());

                BoolExpr conflictExpr = getAllConflictConstraints(p.getConflicts());
                implies.add(conflictExpr);

                finalExpr.add(context.mkImplies(curPackageBoolConst, context.mkAnd(implies.toArray(new BoolExpr[implies.size()]))));


            }
        }


        for(BoolExpr b: finalExpr)
        {
            System.out.println(b.toString());
        }
        BoolExpr finalExprResult = context.mkAnd(finalExpr.toArray(new BoolExpr[finalExpr.size()]));
       // System.out.println(finalExprResult.toString());
        solver.add(finalExprResult);
        if(solver.check() == Status.SATISFIABLE)
        {
            System.out.println("SOLUTION FOUND");

            Model model = solver.getModel();


            System.out.println(model.toString());

            List<FuncDecl> dec = new ArrayList<>(Arrays.asList(model.getConstDecls()));
            dec.forEach(d -> {
                System.out.println(d.getName().toString() + " " + model.getConstInterp(d).getBoolValue());
                System.out.println();


                if(model.getConstInterp(d).getBoolValue().toInt() == 1)
                {
                    String[] packageNameVersionSplit = d.getName().toString().split("=");
                    FinalStatePackage finalStatePackage = new FinalStatePackage(packageNameVersionSplit[0], packageNameVersionSplit[1]);
                    finalStatePackages.add(finalStatePackage);
                }


            });

        }
/*
        List<BoolExpr> implies  = context.mkImplies(curPackageBoolConst, context.mkAnd(depsAndCons));


        BoolExpr constraintBoolExpr = getConstraintsBooleanExpression(constraints);

        BoolExpr finalCNF = context.mkAnd(implies, constraintBoolExpr);

        System.out.println("FINAL CNF: ");
        System.out.println(finalCNF);*/



    }

    public static Package getPackage(String packageName, String packageVersionNumber)
    {
        Package returnPackage = null;
        for(Package p: repo)
        {
            if(p.getName().equals(packageName) && p.getVersion().equals(packageVersionNumber))
            {
                returnPackage = p;
            }
        }

        return returnPackage;
    }

    public BoolExpr getConstraintsBooleanExpression(List<String> constraints)
    {
        ArrayList<Package> allPackagesToBeInstalled = new ArrayList<>();


        for(String cons: constraints)
        {
            String[] splitOperation = cons.split("\\+");


            allPackagesToBeInstalled.addAll(getAllPackageVersions(splitOperation[1]));
        }

        ArrayList<BoolExpr> constraintBoolExprs = new ArrayList<>();
        BoolExpr b = null;
        for(Package p: allPackagesToBeInstalled)
        {
            b = context.mkBoolConst(p.getName() + "=" + p.getVersion());
            constraintBoolExprs.add(context.mkAnd(b));
        }


        BoolExpr implies = context.mkImplies(b, context.mkAnd(constraintBoolExprs.toArray(new BoolExpr[constraintBoolExprs.size()])));

        //System.out.println(implies.toString());

        return context.mkImplies(b, context.mkAnd(constraintBoolExprs.toArray(new BoolExpr[constraintBoolExprs.size()])));
    }

    public BoolExpr getAllConflictConstraints(List<String> conflictsList)
    {

        ArrayList<BoolExpr> conflicts = new ArrayList<>();

        ArrayList<Package> conflictPackages = new ArrayList<>();

        if(!(conflictsList.isEmpty()))
        {
            for(String c: conflictsList)
            {
                conflictPackages.addAll(getAllPackageVersions(c));
            }


            for(Package p: conflictPackages)
            {
                BoolExpr b = context.mkBoolConst(p.getName() + "=" + p.getVersion());
                conflicts.add(context.mkNot(b));
            }
        }


        //System.out.println(context.mkAnd(conflicts.toArray(new BoolExpr[conflicts.size()])).toString());
        return  context.mkAnd(conflicts.toArray(new BoolExpr[conflicts.size()]));
    }

    //List: Optional packages <List<String>> Must be installed
    public List<BoolExpr> getAllDependencyConstraints(List<List<String>> packages)
    {
        ArrayList<BoolExpr> constraints = new ArrayList<>();


        ArrayList<BoolExpr> ors = null;
        int index = 0;



        for(List<String> listS: packages)
        {

            ors = new ArrayList<>();

            for(String s: listS)
            {
                ors.add(getConstraintOrs(getAllPackageVersions(s)));
            }


            constraints.add(context.mkOr(ors.toArray(new BoolExpr[ors.size()])));

        }

       // System.out.println(constraints.toString());
        return constraints;
    }

    public BoolExpr getConstraintOrs(List<Package> packages)
    {
        BoolExpr[] dps = new BoolExpr[packages.size()];


        for(int i = 0; i < dps.length; i++)
        {
            dps[i] = context.mkBoolConst(packages.get(i).getName() + "=" + packages.get(i).getVersion());
        }

        //System.out.println(context.mkOr(dps).toString());
        return context.mkOr(dps);
    }

    public static String getPackageComparator(String packageString)
    {

        String packageComparator = "";
        if(packageString.contains(">="))
        {
            packageComparator = ">=";
        }else if(packageString.contains("<="))
        {
            packageComparator = "<=";
        }else if (packageString.contains("="))
        {
            packageComparator = "=";
        }else if(packageString.contains(">"))
        {
            packageComparator = ">";
        }else if(packageString.contains("<"))
        {
            packageComparator = "<";
        }

        return packageComparator;
    }

    public List<Package> getAllPackageVersions(String _package)
    {

        String packageComparator = "";
        if(_package.contains(">="))
        {
            packageComparator = ">=";
        }else if(_package.contains("<="))
        {
            packageComparator = "<=";
        }else if (_package.contains("="))
        {
            packageComparator = "=";
        }else if(_package.contains(">"))
        {
            packageComparator = ">";
        }else if(_package.contains("<"))
        {
            packageComparator = "<";
        }



        ArrayList<Package> packages = new ArrayList<>();


        if(packageComparator.isEmpty())
        {
            for(Package p: repo)
            {
                if(_package.equals(p.getName()))
                {
                    packages.add(p);
                }
            }
        }else
        {
            String[] versionList = _package.split(packageComparator);

            for(Package p: repo)
            {
                if(versionList[0].equals(p.getName()))
                {
                    if(Main.haveCorrectVersion(versionList[1], p.getVersion(), packageComparator)) {
                        packages.add(p);
                    }
                }
            }
        }

        return packages;
    }



}

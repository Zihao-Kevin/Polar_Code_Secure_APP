package com.example.acousticcommunication;
import org.apache.commons.math3.*;
import org.apache.commons.math3.linear.*;
import org.apache.commons.math3.random.RandomDataGenerator;

public class Math_main {
    double beta = 0.1;
    static final int N = 512;
    int n =  (int) (Math.log(N)/Math.log(2));
    double SigP = 10;
    double NoiseP = 5;

    double Jam = 20;
    double IC = Jam * 2 / 3;

    double SINR_bob = SigP + IC - NoiseP - Jam;
    double SINR_eve = SigP - NoiseP - Jam;
    double N0_bob = Jam + NoiseP - IC;
    double N0_eve = Jam + NoiseP;

    // PCparams
    double Ec = Math.pow(10, SigP / 10);
    double N0 = 2 * (Math.pow(10, N0_bob / 10));
    double N1 = 2 * (Math.pow(10, N0_eve / 10));
    double designSNRdB = SINR_bob;
    RealMatrix LLR = new Array2DRowRealMatrix(1, 2 * N - 1 + 10);
    RealMatrix BITS = new Array2DRowRealMatrix(2, N - 1 + 10);

    RealMatrix info_bob = new Array2DRowRealMatrix(N + 10, 1);
    RealMatrix random_bob = new Array2DRowRealMatrix(N + 10, 1);
    RealMatrix X_random = new Array2DRowRealMatrix(N + 10, 1);
    RealMatrix lookup = new Array2DRowRealMatrix(N + 10, 1);

    RealMatrix u = new Array2DRowRealMatrix(N + 10, 1);


    RealMatrix y_bob, y_eve, z_bob, z_eve;

    int K_info = 0;
    int K_random = 0;
    int K_Xrandom = 0;
    int K = 0;

    double error_bob = 0, error_eve = 0;

    public RealMatrix pcc_mainCH(double designSNR)
    {
        RealMatrix z = new Array2DRowRealMatrix(N + 10, 1);
        z.setEntry(1, 0, -Math.pow(10, designSNR / 10) / 2);

        for (int lev = 1; lev <= Math.log(N)/Math.log(2); lev ++ )
        {
            int B = (int) Math.pow(2, lev);
            for (int j = 1; j <= B/2; j ++ )
            {
                double tmp = z.getEntry(j, 0);
                double w = logdomain_diff(Math.log(2) + tmp, 2 * tmp);
                z.setEntry(j, 0, w);
                z.setEntry(B / 2 + j, 0, 2 * tmp);
            }
        }
        return z;
    }

    public double logdomain_diff(double x, double y)
    {
        return x + Math.log(1 - Math.exp(y - x));
    }

    public void cal()
    {
        z_bob = pcc_mainCH(SINR_bob);
        z_eve = pcc_mainCH(SINR_eve);

        double threshold = -Math.pow(N, beta)*Math.log(2) - Math.log(N);

        for (int i = 1; i <= N; i ++ )
        {
            int good_bob = (z_bob.getEntry(i, 0) < threshold) ? 1 : 0;
            int bad_eve = (z_eve.getEntry(i, 0) > threshold) ? 1 : 0;

            int GB = good_bob & bad_eve;
            int GrB = good_bob & (~bad_eve);
            int rGrB = (~good_bob) & (~bad_eve);

            info_bob.setEntry(i, 0, GB);
            random_bob.setEntry(i, 0, GrB);
            X_random.setEntry(i, 0, rGrB);

            if (GB == 1) K_info ++ ;
            if (GrB == 1) K_random ++ ;
            if (rGrB == 1) K_Xrandom ++ ;

            if (GB == 1 || GrB == 1 || rGrB == 1)
                lookup.setEntry(i, 0, -1);

        }
        // K = 66, K_info = 65, K_Xrandom = 0
        K = K_info + K_random + K_Xrandom;
    }

    // input: K length message
    public void get_u()
    {
        RandomDataGenerator random = new RandomDataGenerator();
        for (int i = 1; i <= K; i ++ )
            u.setEntry(i, 0, random.nextInt(0, 1));
    }
    public void get_my_u()
    {
        double a[] = new double[] {0,0,0,0,1,1,0,0,1,0,0,1,1,0,0,0,1,1,1,0,1,0,1,1,0,0,0,1,0,0,0,1,1,1,0,0,1,0,1,0,0,0,1,1,1,0,0,0,0,1,1,1,1,0,1,0,1,1,1,0,0,1,1,1,1,1};
        for (int i = 1; i <= K; i ++ ) u.setEntry(i, 0, a[i - 1]);
    }

    //产生高斯白噪声 均值为0 方差为1
    public RealMatrix randn(int row, int col)
    {
        RandomDataGenerator random = new RandomDataGenerator();
        RealMatrix rand = new Array2DRowRealMatrix(row, col);
        for (int i = 0; i < row; i ++ )
        {
            double tmp = random.nextGaussian(0, 1);
            rand.setEntry(i, 0, tmp);
        }
        return rand;
    }
    public RealMatrix pencode()
    {
        RealMatrix d = lookup.copy();
        int cnt = 1;
        for (int i = 1; i <= N; i ++ )
        {
            if (lookup.getEntry(i, 0) == -1)
            {
                d.setEntry(i, 0, u.getEntry(cnt, 0));
                cnt ++ ;
            }
        }

        for (int i = 1; i <= n; i ++ )
        {
            int B = (int) Math.pow(2, n - i + 1);
            int nB = (int) Math.pow(2, i - 1);
            for (int j = 1; j <= nB; j ++ )
            {
                int base = (j - 1) * B;
                for (int l = 1; l <= B / 2; l ++ )
                    d.setEntry(base + l, 0, (d.getEntry(base + l, 0) + d.getEntry(base + B / 2 + l, 0)) % 2);
            }
        }
        return d;
    }

    public void get_y(RealMatrix x)
    {
        //RealMatrix x = pencode();
        y_bob = x.scalarMultiply(2).scalarAdd(-1).scalarMultiply(Math.sqrt(Ec)).add(randn(N + 10, 1).scalarMultiply(Math.sqrt(N0 / 2)));
        y_eve = x.scalarMultiply(2).scalarAdd(-1).scalarMultiply(Math.sqrt(Ec)).add(randn(N + 10, 1).scalarMultiply(Math.sqrt(N1 / 2)));
    }
    public void get_my_y()
    {
        y_bob = new Array2DRowRealMatrix(N + 10, 1);
        y_eve = new Array2DRowRealMatrix(N + 10, 1);
        double b[] = new double[] {66.6445013019388,63.7577241220305,17.2331264911614,10.8373609534016,-25.8911313285034,-13.5579764127606,-7.85248573150922,12.6814259401438,5.42658165458541,-1.23328758561419,-2.88893555188624,-13.3018941589381,3.25691217405641,23.4195385073093,4.43739941135521,0.0569797765653379,14.8541044787962,13.5517969744007,-25.5827429006537,3.49613809723701,-4.41249479945830,-33.0084103908279,-20.4630988206848,-2.52618243571528,11.5161823352000,-7.23974447609873,1.50127455531511,-19.1308630103352,-41.5251474942432,-1.49217531694872,-20.0769418017303,17.6896476370243,27.6925748759222,-9.77207190257136,18.0501795299588,13.7962488342362,-22.0011679486311,2.56910912438017,7.80568108514632,12.4625817799110,8.87288341454245,8.83036146101957,8.90155135273083,-8.72081845135358,9.90629145833606,-36.9005477799104,-17.6142622152842,24.5365848264325,-8.58750066036995,8.98719262499933,23.6564356654986,-18.9695626318059,-13.8964834077450,-5.59964827668036,4.68094067391527,-6.80355812687277,-14.2258616232962,-24.8091076355140,-9.74656284306492,-7.09486195211708,-6.35530175578080,-1.49631955330778,-22.6418156131913,6.60462840247278,-41.8331074370889,5.73393023091177,15.0595614864253,-18.2568318345499,-4.17749949494313,8.35105272049367,-7.76735728929251,-3.40489167332920,4.16625178783363,-13.5028606354102,18.0137032800931,16.3016744251448,2.54921318322920,3.43102615626966,-17.5456534317248,5.63737346372583,-18.4589144941285,25.1263773879043,12.5244750483355,1.54693683111881,-10.1615479066896,-35.5385956501754,-24.4243542400972,9.65883724181009,39.7377005044103,16.3259415671172,2.34587859177564,-6.47576882218564,5.33681434564329,13.9497375815415,-1.35410468532785,30.4784295850850,-24.8618057326355,-3.52827000430069,-2.22994090260435,-31.5841230235647,13.2170352572347,-1.77887475315376,4.59737026338415,10.9698476850294,21.7219831224236,11.1028067661985,-5.17472948894933,-3.99082025946648,3.12310488152521,-19.1871479801911,-21.5858114015104,-4.43031190487930,-1.78111152577945,35.0100217034216,17.3398438409401,-11.0311772235238,-14.3492750109655,30.4374609451053,51.7722634261612,-0.184743020892670,-11.6778383019296,-21.9586554795690,20.5002894937989,1.98973538316375,17.5619373118981,-12.9970472126976,7.44037401291320,-14.7930489709952,25.8345317022335,-12.9639868045100,-4.71665156737144,-3.02293944337645,14.5131534282217,10.7810699102095,-14.6949424155936,-14.4132359006803,-21.7579231228413,12.1551612844970,9.06534233862486,-32.2444818203292,-30.7466690933133,-3.88067979838619,9.23576776909862,6.16405910369880,-30.6152898535842,-24.4331047993446,12.2567883906128,0.577139703711249,-10.0307336408181,26.5680643844017,-17.3256370100132,5.57080368908417,4.26748806204972,17.7420285914409,-18.3550678900313,9.51640054210608,45.7131371947665,-23.7426806943694,4.55508212353061,-0.923634634935433,-17.8701843931322,-11.6326978844764,5.69176664139116,44.3405798694303,-17.2787166642525,12.7820502973450,-13.1039506631682,38.3111449012095,6.52330564824436,0.709931520903931,7.86084313823355,3.06300017166056,16.5268185493165,-0.138033025238247,-23.1042197205765,7.54483866643428,24.5973259872567,-18.1169999059552,-1.14729851956857,14.7063746766018,-30.9829241792560,7.95989585694048,10.2274624150025,6.95861404804530,-10.5346362667576,-3.28915171706483,-1.50516087712043,19.7936573133380,8.63692492568117,-3.06583773961402,31.9682246320835,-4.06623214838612,-11.2897470152636,-4.63405376805317,12.1062070869709,0.309207121289901,12.6441750059563,-1.93132026937340,47.3849091634742,-26.4575109498487,-4.23408334373317,1.66452882511015,-1.57512865458395,22.7320881857429,0.740219076378568,-5.20511617786820,4.38222124976456,16.5259559691691,-9.09766175811660,11.1790028114245,-24.6682335925299,1.76106597372942,-13.5854150110441,-8.44742407383144,8.18643696095164,-16.8447713079034,-14.4244449021193,-23.7939265728366,-36.4668653006271,0.467798853155303,23.8391213051340,-0.0730578559217729,-0.998069976594082,-15.4515787824300,31.3181112591147,-1.27298986067703,26.1735512477363,9.59929592123573,-7.84008626003630,16.0812743151423,14.3081453051071,-5.14406273084822,19.8833483224279,28.5141249392782,-4.32909348535836,-7.70899784445035,10.3578746919622,9.41749557800556,17.5220805776393,-1.38283543855801,9.55443453493172,15.4847124487064,6.31958705684931,5.88833882553078,-25.4676715931465,-24.0179538442343,-22.8262412270592,-18.9646394161325,0.404177403491292,-25.5554021752050,36.7790364345151,9.98420649799796,-33.6062340961640,-17.3688576074290,-5.06844875814187,-33.6220537747739,-11.3404876106944,22.5633078619525,0.248028797144586,-4.35800109773673,-20.9733900774463,-6.71893544375126,-20.8721704939754,3.31441730105513,-4.87923706979497,10.7681580875671,18.8392469025331,22.3178711259133,2.54270513289688,-12.1245806492311,-24.9287550641626,5.26970027840444,13.8998420481884,-2.90041813190539,-6.24436185388276,14.1218226921154,18.5251779288844,-28.3589705494316,6.71798217018911,0.320070942853409,-5.84983406351517,19.0384746074978,-9.63859678824151,-11.5296656765711,8.07052987114269,5.01420307094350,-0.705598674753730,10.9950131482552,-23.8324051042891,-22.2773059220893,-51.4694130293270,6.10979901762559,-14.4608914380675,-7.66356053169930,21.1098340133732,-2.25514082330256,-4.66984797743632,-18.2504345018718,-1.11218748202015,7.55949236015594,-23.8009488199965,-4.37770500330897,17.0754088878067,-28.4267542232938,8.74090463923755,15.3121341106639,-12.5245691659713,-29.9166470811004,10.8647064066125,11.2107672723541,7.13285733859694,16.7278665940594,-17.8592439849864,7.22016559894270,-13.4764704093432,-6.82039947995337,-30.8304644385831,22.7270581059735,6.27530339844083,14.6847356283065,23.7076695870395,-23.3855651792001,14.5969413485202,2.93536842400806,13.0958147839517,16.5356269115627,17.7525527246689,-11.3771452068633,-5.41153830382456,1.53492124654979,59.3297320413881,24.9749592747821,38.1051024415511,4.20865245062712,6.92923479618506,14.0660982444897,-6.22307136495505,25.2414170677560,0.359163683809512,-21.2642000511868,12.3236579399431,-31.0551824117486,-35.9582471772813,1.96645460232471,-13.7475791281679,-13.0330836492590,0.282368909948604,-5.53176177492833,-7.12396423655689,1.67582482760382,-23.9420219450989,-24.9325842709235,-34.1723197734277,-3.77732667937475,14.9750633830419,-9.32593858363099,5.75272665929497,6.44601042918466,14.4560431380872,14.4316296765739,-15.4117985838705,-18.3992443958163,-2.30176793961091,-14.9852288303057,28.9960888334463,27.6997905608496,4.85406901015482,-4.43323497336190,12.2458677674527,-8.50810107570559,-5.38542524595771,-12.5954577688453,5.03989885813396,23.2275006143139,10.0410564686956,17.1741817691968,-19.4284527406820,0.0350984505695386,-14.3243941555299,10.0056048977979,2.62953627293397,19.8897427141280,22.7096492341839,14.7784086377035,-12.1438937116020,-11.6267775228144,-33.3202166885585,-4.80982963227885,-24.4223309285015,19.6971780279251,12.8767351503108,-0.703140426977948,-9.89142491559130,-0.618195427947544,31.7043497882813,27.1533306711609,-4.81227607192946,6.15710150429293,-23.0878396777465,3.96383755868815,16.3048764982107,19.1675873237427,-24.0670615963903,12.1358452117913,-12.2058734471047,-3.53392151850018,17.1753044420794,8.46520718248321,0.250490290648140,13.9516775914128,32.2282311245817,-7.92518286224000,-20.8462756057012,-17.5056143652246,-13.6296449521689,-8.77372828153872,13.4172801653927,-33.9285423512548,7.35837481696485,-10.6802289302335,22.7155613357579,-12.0520460487719,3.29667332388013,-13.5106170178948,-15.2823131613558,-7.78828358820521,-7.23089851479552,-12.4949190734998,16.9023781673828,6.62073534062665,29.8274258810265,-8.09342502759262,22.1299530397340,-34.1680545945310,8.19174774783858,3.44341607097193,-9.81623110716899,9.20295845182953,18.7689688556663,21.5426740899843,4.30278495509564,7.52641265932392,-8.82365123255416,-16.3130553421084,-1.59669547052322,6.47966451304036,13.7481756398483,-17.2199095908997,-5.54200850921054,49.7321787377345,-14.2335848713318,37.3106303437031,20.2541911243972,-13.0815764852245,-5.05712598079592,-0.663890484775647,5.25769204307507,27.4471896383200,-25.9624485995414,32.8252760605540,10.6689890589385,-6.59873222193508,0.921443588907830,2.16509903935701,8.42902884387288,18.1909977218596,22.5485965718321,-9.54102325964136,-5.47275396112824,16.2101809692031,-1.02244612144743,23.3694229256102,-9.05046730369763,5.22556158111647,8.28981235376985,21.5887902891002,-27.0890628685229,3.17556863058535,-2.21099051164413,-38.4833402057951,19.0323413131339,53.1182692923710,-7.29117325035168,8.26651122856622,-11.4242436420584,9.99652155256193,-9.91667230162113,1.35237639582424,25.6129709160481,-11.9038368986910,-13.4030896175306,1.81233012887406,-5.51799270801574,28.7572250997794,32.9592525214729,-14.2328801600241,-8.23691424121639,6.09323400509756,-9.93218730579380,38.6007534038604,25.9280661376259,13.1031955029088,13.1853287709192,-9.17991510144537,10.4175063140374,23.2113527598094,5.67980857815903,31.1253340908885,15.0880935216708,-5.12036311586792,3.43446779307293,22.6627953035613};
        double a[] = new double[] {1.32080460025798,6.36835546417856,6.56261295614364,-8.23221844200883,-2.67058213097878,-8.68830170460115,8.15242998425565,8.56391205808085,-9.53179042502817,4.28434075313622,-0.993404785779517,4.03126353450051,7.37248076932240,3.72618527304283,5.63284701362466,7.38328588312078,3.69219870854241,-4.14404931858580,1.76392130377536,-0.0869902631549535,0.232364626860305,-1.15813188359946,3.46183392679311,11.2331694939958,-5.90482277903255,-4.23698557452720,1.30670603747066,1.48426781026266,-1.30207941563084,0.768583209174651,0.173636421374754,-4.62486342402659,-1.51910833486456,-4.30830257478138,-6.60984210032117,-0.730449196184114,3.42070497030619,2.44538388949006,4.27994004942837,6.94632057519796,-1.65687190331704,-2.41691499443648,-2.09036921418926,-2.96604307499970,0.195152770961663,6.17658540253614,8.56006344306452,1.11604618957779,10.5478804055861,2.48704037484828,-4.09613084350133,-6.60115349997193,0.126687082150805,-0.488746443773125,-1.80639973791119,2.95622859039686,5.18311474987600,6.43488628320291,1.97858708921826,-12.7384573129181,2.52032658855108,4.51474529087713,-0.414344619274522,-8.16140809910021,-7.01594956046826,6.19153377162665,2.71567111877515,-1.04328695510597,-6.84268740310391,-3.09712459336813,6.07857816993590,1.40950500691909,3.09043848319277,-2.70782699774243,-1.95945369259481,-8.80885898160470,-3.32189668252507,0.804154052018354,8.19705028183287,-8.73690961620460,-3.51298776112158,-2.37571705738681,7.73261691543726,-6.23804295691893,-8.01116727736723,2.59016134362493,-9.43181065389641,3.22872709064501,0.0114287396374091,3.99647395713182,-10.4769516184704,1.10561295789633,2.00513549021910,3.78589435034439,6.66724566494754,-3.38096454834324,1.85413311372070,-0.840300583519662,-4.49655288406211,8.57406340334983,8.91818737915415,-0.364070754664800,5.04244131067493,-5.40783641829527,6.01612740511370,-6.33509408988332,5.36337393206171,-2.08249066131681,1.20261753535947,1.53069837056475,-0.725106873746530,6.20109109494519,-6.60412824565439,3.76088085287565,9.28165584045895,-2.73150011957361,-4.34467745085043,4.91182594933432,2.10831497579524,-1.46451169373058,-3.67858804144788,3.09205878741786,4.92764804567107,2.05700743176838,-1.43106175997002,9.47754345006882,-4.60875601039297,-4.88346444825016,4.06641171115212,-6.36198133612067,-8.05070030284267,5.52625434928608,-0.814903716461097,4.27095093684382,4.67680758960442,-6.49756657237041,-5.06901482992190,2.75359799744447,0.527075721774807,-1.89078000865762,12.2238955902609,-5.00979364722228,-0.681782604886957,-0.800796713356173,1.96980629958587,-0.550680126458304,-2.36264919327689,0.792330931887533,-1.20065005911767,3.20577761117746,-3.33080630665145,14.4608022225841,-5.57610235142398,2.98267392114251,7.11689439372700,-1.23090861723059,-1.04363613871635,-0.961835640842162,0.786295161018128,4.41710557056520,-0.663865658960991,2.09390861821049,-2.22290285067092,8.80375193602546,-11.8786167706172,-3.09516428665377,-1.57053853241582,0.653735845430230,-4.29764342904259,-2.57269825413156,-6.62886749579671,1.61378264001109,-5.94294604936795,-0.157388374327677,-4.77845149792574,-0.449253164936021,1.97872959102532,-0.624600411221646,10.1271557783169,-4.59681426301454,-2.39961229494592,-5.53256326538118,0.417970137477028,7.20773994170607,3.77613829578817,-2.06119333269425,-0.737498136396513,-2.42758436161824,-5.39091631063781,-3.84936635540053,1.44167984655360,0.474571100886360,5.91092975336110,11.9273765282551,-2.52351282888559,-11.4241966692723,3.31012675009585,1.75037947553315,0.930109571416308,4.01515533089285,6.14850189286686,4.63616266324134,-0.494365862751160,-3.59411202821552,-3.31101851261508,-2.82479366271545,0.136958079907543,8.61390567034951,3.18653552514132,5.79231405686269,-0.113132834309498,-0.957149748333205,2.81376512365497,2.19386153787404,7.73987096165729,5.48422097347863,5.23308943317015,-2.37355524113468,-0.545138130317693,3.93637409008276,1.82949214443408,8.10484580282343,8.30048853429467,0.937133190284146,6.51508567009833,8.50850691620142,4.39203202141731,9.38175918666842,0.908102862220577,-2.34200048778893,0.196919236853419,-2.41746807504827,-4.75180704665472,-1.78895527084462,-3.02350185070186,-4.55924779629012,9.94738453563742,4.01001612736125,13.6228643576408,2.02761273819464,-1.00035596233136,2.90103781193578,13.6184682176276,4.32528914092190,-6.18990555112374,-0.0843778781947266,-1.89451621580335,2.11312338708009,4.20385063504182,8.86903547091035,2.34368005055910,-3.26787052022639,6.70203116457530,1.93139234017519,-0.629384690433838,4.17557129019965,-2.56170230449514,2.01086633468073,1.24655099640460,-0.417341928838891,1.96112532493713,11.3061547189343,3.36933715877973,-2.53765659499805,0.738259916453488,3.01356294832084,-3.45133582008982,1.34921773991521,11.5305122390424,-0.0594718161105217,-0.417834528646324,-0.690253246483019,-1.49959385466915,5.15504495211449,-7.34687049150872,2.29721692571947,1.61094667475376,-1.13995037563382,-0.695585222822242,1.00998738218681,3.67591086310283,1.99853733719254,-3.19559608038264,5.10329111135501,7.77216085592294,-1.16233019891086,4.68343542764504,-5.01201620242243,2.27536865294376,-0.812284300963503,9.60959250473417,-0.984655674701567,-1.45824575889140,-1.50314239290825,2.80934585994476,-4.09729772907094,2.32252493673205,-6.53282833467472,1.93321634860599,-6.16751921819657,1.76403802254703,-2.71299218537416,3.83020558314558,-3.98849767384209,-3.74696075399770,3.29134352509455,4.91804241926530,1.74789101925219,5.53795851123969,-4.26057879213871,-0.871167719900496,2.22159610321893,-3.66005918769615,-12.1549897268673,-9.72738143447986,2.25379881627967,-5.53617993359866,0.403208684053182,3.31804288912816,-5.68695864346020,-5.57789856238903,-0.826690027674576,6.15954911109238,12.4972876106654,-2.00369834272052,-2.93884340288213,0.962663173561375,2.41438247639088,-3.35587242611563,-9.88897990698866,2.17629251706788,6.03391267459462,-5.34898237500488,-1.26877724960449,6.96067123151499,7.28900269788805,6.13850374164545,-5.49559297671608,1.00004510291284,6.61605979128491,-1.65020195378191,-3.14367945585430,-1.48836075814025,1.16724208372630,3.75140210128196,0.255835273536282,2.47203917169417,2.36619693039653,0.273321094366219,-1.58264981413951,-1.06462743458114,-2.59589398541987,1.77436449851301,3.39652138561621,3.99251711554813,-2.19419054303966,-2.47699183940801,0.391470798477810,-3.58438991856830,2.86187241525852,-1.01493339052609,-4.77269638553075,-3.75207121260596,-4.21661489230172,4.08605517567727,6.05362224742265,2.04391629085680,4.91866757117732,9.88711638328509,6.73099216496875,-0.000535384755114166,-6.28395603667263,1.11564163466370,4.09153838670623,-3.54787875908816,-9.38814190970282,-8.96431572593336,0.769245645732765,0.257749643986547,11.1248048850634,-11.6751235219425,-1.44298030972547,3.16467642528238,0.264983563969941,-1.61323370333166,-6.20367635643116,6.45626773313084,3.41846610052462,-3.11852176389686,-6.12739232140839,-4.24968314133050,1.22752036458020,-2.46818426051650,-2.95443489798276,5.79719041616083,-2.17612607127187,8.62312012445760,-0.262233141282068,-3.01759403832641,-4.55517953598158,3.73525648903733,-4.28753453688700,9.00060374022419,5.25337839840779,10.8626854457436,8.58980766620144,3.20626223506510,-0.435395749841058,-3.49980528857747,3.22729662326798,4.00191709746656,7.16901182871770,-0.481114577878232,6.20729509469808,3.43593860847539,0.198138574507689,6.12823633478283,4.18005052221174,2.26154444183701,10.3545349452082,5.49638764703327,2.73979782508180,4.22526907658958,3.49576682300255,-2.49721024432678,-1.42448519829755,-5.47446800396144,-6.21582525188855,-1.78056434678319,4.05986078427225,-4.33776942292248,-7.04221238153943,2.24836524697038,1.30792648019629,-4.41639568693524,-1.34047941320047,-3.66035068949110,-5.43935613684649,1.46212370990535,-1.90458053441355,-6.45526384911442,-8.57574917056772,1.18543926414483,-0.994935898471513,0.453020966693689,2.00658273136337,-7.76138953200455,-3.23645811997520,4.46630088966708,-6.85259553225549,1.10536790266523,-2.91438859002662,1.66123911478338,-8.85038799336858,-4.48260873486865,-1.90989154778397,-5.36457776447334,0.324491785973196,-1.37768593807303,-11.0731422781096,-0.997427938252092,10.8251967923019,11.6802615841846,-5.04790195485218,2.98603182248856,-4.94664146414431,3.45324646338586,-6.68225583864516,-10.5161125873926,3.02265716991075,-7.85596319786146,-4.12630368031153,12.2584674857079,-4.05626869011798,4.70962023733475,1.40629235612594,-9.61690360537397,4.74512687502649,-1.23999797420008,3.48051268702868,-2.55772383856226,1.13963124206190,-0.392097015552580,-0.0940076201484978,-6.21337376291774,5.94118142416375,3.29918936123698,1.68192939924491,-5.09732743654899,4.72568499890418,0.946119088572984,6.87080098333023,-2.12729964761968,0.731747683747345,-0.749537245358956,2.85871834398799,8.43762719597108,-8.55740283298295,-2.62119269233883,8.10343364568290,-5.05847109896046,0.768456519462715,-0.414649769415435,-4.23012488458663,-3.93052624760167,3.68615860670563,-0.221996870529267,-2.84605945603743,0.848720091964497,-4.57494453687467,-6.37346516081865,2.08006299619699};
        for (int i = 1; i <= N; i ++ )
        {
            y_bob.setEntry(i, 0, a[i - 1]);
            y_eve.setEntry(i, 0, b[i - 1]);
        }
    }
    public RealMatrix pdecode(RealMatrix y)
    {
        RealMatrix initialLRs = y.scalarMultiply(4 * Math.sqrt(Ec) / N0).scalarMultiply(-1);
        RealMatrix u_d = new Array2DRowRealMatrix(K + 1, 1);

        for (int i = N; i <= 2 * N - 1; i ++ ) LLR.setEntry(0, i, initialLRs.getEntry(i - N + 1, 0));
        RealMatrix d_hat = new Array2DRowRealMatrix(N + 10, 1);

        for (int j = 1; j <= N; j ++ )
        {
            int i = bitreversed(j - 1) + 1;
            updateLLR(i);

            if (lookup.getEntry(i, 0) == -1)
            {
                if (LLR.getEntry(0, 1) > 0) d_hat.setEntry(i, 0, 0);
                else d_hat.setEntry(i, 0, 1);
            }
            else d_hat.setEntry(i, 0, lookup.getEntry(i, 0));

            updateBITS(d_hat.getEntry(i, 0), i);
        }


        int cnt = 0;
        for (int i = 1; i <= N; i ++ )
            if (lookup.getEntry(i, 0) == -1)
            {
                u_d.setEntry(cnt, 0, d_hat.getEntry(i, 0));
                cnt ++ ;
            }

        return u_d;
    }
    public int bitreversed(int j)
    {
        String binaryString = Integer.toBinaryString(j);

        StringBuffer sb = new StringBuffer(binaryString);
        String str = sb.reverse().toString();
        while (str.length() < n) str += '0';
        int res = Integer.parseInt(str,2);
        return res;
    }
    public void updateLLR(int i)
    {
        int nextlevel;

        if (i == 1) nextlevel = n;
        else
        {
            String i_bin = Integer.toBinaryString(i - 1);
//    		int lastlevel = 0;
//    		for (; lastlevel < n; lastlevel ++ )
//    			if (i_bin.charAt(lastlevel) == '1') break;

            int lastlevel = n - i_bin.length() + 1;

            int st = (int) Math.pow(2, lastlevel - 1);
            int ed = (int) Math.pow(2, lastlevel) - 1;

            for (int idx = st; idx <= ed; idx ++ )
            {
                LLR.setEntry(0, idx, lowerconv(BITS.getEntry(0, idx), LLR.getEntry(0, ed + 2 * (idx - st) + 1), LLR.getEntry(0, ed + 2 * (idx - st) + 2)));
            }
            nextlevel = lastlevel-1;
        }
        for (int lev = nextlevel; lev >= 1; lev -- )
        {
            int st = (int) Math.pow(2, lev - 1);
            int ed = (int) (Math.pow(2, lev) - 1);
            for (int idxx = st; idxx <= ed; idxx ++ )
            {
                LLR.setEntry(0, idxx, upperconv(LLR.getEntry(0, ed + 2 * (idxx - st) + 1), LLR.getEntry(0, ed + 2 * (idxx - st) + 2)));
            }
        }
    }
    public void updateBITS(double latestbit, int i)
    {
        if (i == N) return;
        else if (i <= N / 2) BITS.setEntry(0, 1, latestbit);
        else
        {
            String i_bin = Integer.toBinaryString(i - 1);
            int lastlevel = 0;
            for (; lastlevel <= n; lastlevel ++ )
                if (i_bin.charAt(lastlevel) == '0') break;

            lastlevel = n - i_bin.length() + lastlevel + 1;

            BITS.setEntry(1, 1, latestbit);
            for (int lev = 1; lev <= lastlevel - 2; lev ++ )
            {
                int st = (int) Math.pow(2, lev - 1);
                int ed = (int) (Math.pow(2, lev) - 1);
                for (int idx = st; idx <= ed; idx ++ )
                {
                    BITS.setEntry(1, (ed + 2 * (idx - st) + 1), (BITS.getEntry(0, idx) + BITS.getEntry(1, idx)) % 2);
                    BITS.setEntry(1, (ed + 2 * (idx - st) + 2), BITS.getEntry(1, idx));
                }
            }
            int lev = lastlevel - 1;
            int st = (int) Math.pow(2, lev - 1);
            int ed = (int) Math.pow(2, lev) - 1;

            for (int idx = st; idx <= ed; idx ++ )
            {
                BITS.setEntry(0, (ed + 2 * (idx - st) + 1), (BITS.getEntry(0, idx) + BITS.getEntry(1, idx)) % 2);
                BITS.setEntry(0, (ed + 2 * (idx - st) + 2), BITS.getEntry(1, idx));
            }

        }
    }
    public double lowerconv(double upperdecision, double upperllr, double lowerllr)
    {
        double llr;
        if (upperdecision == 0) llr =  lowerllr + upperllr;
        else llr = lowerllr - upperllr;
        return llr;
    }
    public double upperconv(double llr1, double llr2)
    {
        double llr;
        llr = logdomain_sum(llr1 + llr2, 0) - logdomain_sum(llr1, llr2);
        return llr;
    }
    public double logdomain_sum(double x, double y)
    {
        double z;
        if (x < y)	z = y + Math.log(1 + Math.exp(x - y));
        else z = x + Math.log(1 + Math.exp(y - x));
        return z;
    }
    public void get_Error()
    {
        RealMatrix u_decoded_bob = pdecode(y_bob);
        System.out.println(u_decoded_bob);
        RealMatrix u_decoded_eve = pdecode(y_eve);
        System.out.println(u_decoded_eve);

        for (int i = 1; i <= K; i ++ )
        {
            error_bob += (int) (u.getEntry(i, 0)) ^ (int) (u_decoded_bob.getEntry(i - 1, 0));
            error_eve += (int) (u.getEntry(i, 0)) ^ (int) (u_decoded_eve.getEntry(i - 1, 0));
        }
        error_bob = error_bob / K;
        error_eve = error_eve / K;
    }

}


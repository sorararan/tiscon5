package com.tiscon.service;

import com.tiscon.code.OptionalServiceType;
import com.tiscon.code.PackageType;
import com.tiscon.dao.EstimateDao;
import com.tiscon.domain.Customer;
import com.tiscon.domain.CustomerOptionService;
import com.tiscon.domain.CustomerPackage;
import com.tiscon.domain.Prefecture;
import com.tiscon.dto.UserOrderDto;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static java.lang.Math.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 引越し見積もり機能において業務処理を担当するクラス。
 *
 * @author Oikawa Yumi
 */
@Service
public class EstimateService {

    /** 引越しする距離の1 kmあたりの料金[円] */
    private static final int PRICE_PER_DISTANCE = 100;

    private final EstimateDao estimateDAO;
    private final SearchPositionService searchPositionService;

    /**
     * コンストラクタ
     * @param estimateDAO EstimateDaoクラス
     * @param searchPositionService SearchPositionServiceクラス
     */
    public EstimateService(EstimateDao estimateDAO, SearchPositionService searchPositionService) {
        this.estimateDAO = estimateDAO;
        this.searchPositionService = searchPositionService;
    }

    /**
     * 見積もり依頼をDBに登録する。
     *
     * @param dto 見積もり依頼情報
     */
    @Transactional
    public void registerOrder(UserOrderDto dto) {
        Customer customer = new Customer();
        BeanUtils.copyProperties(dto, customer);
        estimateDAO.insertCustomer(customer);

        if (dto.getWashingMachineInstallation()) {
            CustomerOptionService washingMachine = new CustomerOptionService();
            washingMachine.setCustomerId(customer.getCustomerId());
            washingMachine.setServiceId(OptionalServiceType.WASHING_MACHINE.getCode());
            estimateDAO.insertCustomersOptionService(washingMachine);
        }

        List<CustomerPackage> packageList = new ArrayList<>();

        packageList.add(new CustomerPackage(customer.getCustomerId(), PackageType.BOX.getCode(), dto.getBox()));
        packageList.add(new CustomerPackage(customer.getCustomerId(), PackageType.BED.getCode(), dto.getBed()));
        packageList.add(new CustomerPackage(customer.getCustomerId(), PackageType.BICYCLE.getCode(), dto.getBicycle()));
        packageList.add(new CustomerPackage(customer.getCustomerId(), PackageType.WASHING_MACHINE.getCode(), dto.getWashingMachine()));
        estimateDAO.batchInsertCustomerPackage(packageList);
    }

    /**
     * 見積もり依頼に応じた概算見積もりを行う。
     *
     * @param dto 見積もり依頼情報
     * @return 概算見積もり結果の料金
     */
    public Integer getPrice(UserOrderDto dto) {
        double distance = java.lang.Double.NaN;
//        try{
//            // DAOから住所文字列取得
//            List<Prefecture> prefectures = estimateDAO.getAllPrefectures();
//            String address_from = "";
//            String address_to = "";
//            address_from += prefectures.get(Integer.parseInt(dto.getOldPrefectureId())-1).getPrefectureName();
//            address_to += prefectures.get(Integer.parseInt(dto.getNewPrefectureId())-1).getPrefectureName();
//            address_from += dto.getOldAddress();
//            address_to += dto.getNewAddress();
//
//            // 距離の計算
//            Double[] pos_from = searchPositionService.search(address_from);
//            Double[] pos_to = searchPositionService.search(address_to);
//            distance = calcDistance(pos_from[0], pos_from[1], pos_to[0], pos_to[1]);
//        }catch(Exception ignored){
//        }
        // yolp apiからの値で計算できなかったときの処理
        if(java.lang.Double.isNaN(distance)){
            distance = estimateDAO.getDistance(dto.getOldPrefectureId(), dto.getNewPrefectureId());
        }

        // 距離当たりの料金を算出する
        int priceForDistance = (int) Math.floor(distance * PRICE_PER_DISTANCE);

        int boxes = getBoxForPackage(dto.getBox(), PackageType.BOX)
                + getBoxForPackage(dto.getBed(), PackageType.BED)
                + getBoxForPackage(dto.getBicycle(), PackageType.BICYCLE)
                + getBoxForPackage(dto.getWashingMachine(), PackageType.WASHING_MACHINE);

        // 箱に応じてトラックの種類が変わり、それに応じて料金が変わるためトラック料金を算出する。
        int pricePerTruck = estimateDAO.getPricePerTruck(boxes);

        // オプションサービスの料金を算出する。
        int priceForOptionalService = 0;

        if (dto.getWashingMachineInstallation()) {
            priceForOptionalService = estimateDAO.getPricePerOptionalService(OptionalServiceType.WASHING_MACHINE.getCode());
        }

        return priceForDistance + pricePerTruck + priceForOptionalService;
    }

    /**
     * 荷物当たりの段ボール数を算出する。
     *
     * @param packageNum 荷物数
     * @param type       荷物の種類
     * @return 段ボール数
     */
    private int getBoxForPackage(int packageNum, PackageType type) {
        return packageNum * estimateDAO.getBoxPerPackage(type.getCode());
    }

    /**
     *
     * @param x_1 1つ目のアドレスの緯度
     * @param y_1 1つ目のアドレスの経度
     * @param x_2 2つ目のアドレスの緯度
     * @param y_2 2つ目のアドレスの経度
     * @return 直線距離
     */
    private double calcDistance(double x_1, double y_1, double x_2, double y_2) {
        // (x緯度 y経度)
        double r = 6378.137; // 赤道半径[km]

        // (lat = 緯度, lng = 経度)
        double lat1 = x_1 * PI / 180;
        double lng1 = y_1 * PI / 180;

        double lat2 = x_2 * PI / 180;
        double lng2 = y_2 * PI / 180;

        // 2点間の距離[km]
        double distance = r * acos(sin(lat1) * sin(lat2) + cos(lat1) * cos(lat2) * cos(lng2 - lng1));

        return distance;
    }
}
